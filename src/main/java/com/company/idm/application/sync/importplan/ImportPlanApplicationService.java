package com.company.idm.application.sync.importplan;

import com.company.idm.application.sync.feishu.FeishuDepartmentPayload;
import com.company.idm.application.sync.feishu.FeishuFullImportDocument;
import com.company.idm.application.sync.feishu.FeishuImportDocumentResolver;
import com.company.idm.application.sync.feishu.FeishuImportUploadService;
import com.company.idm.application.sync.feishu.FeishuUserPayload;
import com.company.idm.application.user.IntranetEmailGenerationService;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ChangeItemStatus;
import com.company.idm.domain.sync.ChangeType;
import com.company.idm.domain.sync.ImportBatch;
import com.company.idm.domain.sync.ImportBatchRepository;
import com.company.idm.domain.sync.ImportBatchStatus;
import com.company.idm.domain.sync.ImportSourceType;
import com.company.idm.domain.sync.RiskLevel;
import com.company.idm.domain.sync.TargetType;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ImportPlanApplicationService {

    private final ImportBatchRepository importBatchRepository;
    private final FeishuImportDocumentResolver importDocumentResolver;
    private final FeishuImportUploadService uploadService;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final IntranetEmailGenerationService intranetEmailGenerationService;
    private final ImportDiffPolicyService diffPolicyService;
    private final ImportConflictDetector conflictDetector;
    private final ImportJsonService jsonService;

    @Transactional
    public ImportBatch generatePlanFromUpload(MultipartFile file, String createdBy, String remark) {
        String documentPath = uploadService.store(file);
        String fileName = file.getOriginalFilename() == null ? Paths.get(documentPath).getFileName().toString() : file.getOriginalFilename();
        return generatePlan(documentPath, fileName, createdBy, remark, ImportSourceType.FEISHU_EXPORT);
    }

    @Transactional
    public ImportBatch generatePlan(String documentPath, String createdBy, String remark) {
        return generatePlan(documentPath, Paths.get(documentPath).getFileName().toString(), createdBy, remark, ImportSourceType.MANUAL_FILE);
    }

    @Transactional(readOnly = true)
    public ImportBatch findById(Long batchId) {
        return importBatchRepository.findById(batchId)
            .orElseThrow(() -> new BizException("IMPORT_BATCH_NOT_FOUND", "导入批次不存在"));
    }

    @Transactional(readOnly = true)
    public List<ImportBatch> listRecent(int limit) {
        return importBatchRepository.findRecent(limit);
    }

    @Transactional
    public ImportBatch confirmPlan(Long batchId, List<Long> enabledItemIds, List<Long> confirmedItemIds, String confirmedBy) {
        ImportBatch batch = findById(batchId);
        batch.applyConfirmations(enabledItemIds, confirmedItemIds);
        batch.confirm(confirmedBy);
        return importBatchRepository.save(batch);
    }

    private ImportBatch generatePlan(
        String documentPath,
        String fileName,
        String createdBy,
        String remark,
        ImportSourceType sourceType
    ) {
        if (importBatchRepository.existsActiveFeishuImportBatch()) {
            throw new BizException("IMPORT_BATCH_ACTIVE_EXISTS", "当前已有未完成的飞书导入计划");
        }
        FeishuFullImportDocument document = importDocumentResolver.resolveFullImportDocument(documentPath);
        ImportBatch batch = ImportBatch.builder()
            .batchCode(buildBatchCode())
            .fileName(fileName)
            .fileHash(sha256(documentPath))
            .sourceType(sourceType)
            .changeItems(new ArrayList<>())
            .rollbackItems(new ArrayList<>())
            .status(ImportBatchStatus.DRAFT)
            .createdBy(createdBy)
            .createdAt(LocalDateTime.now())
            .expiredAt(LocalDateTime.now().plusHours(24))
            .remark(remark)
            .build();

        List<ChangeItem> conflicts = new ArrayList<>();
        conflictDetector.detectFileLevelConflicts(document.departments(), document.users(), conflicts);
        conflicts.forEach(batch::addChangeItem);
        Map<String, Department> plannedDepartments = generateDepartmentItems(document.departments(), batch);
        generateUserItems(document.users(), batch, plannedDepartments);
        generateResignItems(document.users()).forEach(batch::addChangeItem);
        return importBatchRepository.save(batch);
    }

    private Map<String, Department> generateDepartmentItems(List<FeishuDepartmentPayload> payloads, ImportBatch batch) {
        Map<String, FeishuDepartmentPayload> payloadByExternalId = new LinkedHashMap<>();
        for (FeishuDepartmentPayload payload : payloads) {
            payloadByExternalId.put(payload.externalId(), payload);
        }
        Map<String, Department> existingByExternalId = new LinkedHashMap<>();
        Map<String, Department> existingByCode = new LinkedHashMap<>();
        for (Department department : departmentRepository.findAll()) {
            if (department.getExternalId() != null && !department.getExternalId().isBlank()) {
                existingByExternalId.put(department.getExternalId(), department);
            }
            existingByCode.put(department.getDeptCode(), department);
        }
        Map<String, Department> plannedByExternalId = new LinkedHashMap<>();
        LinkedHashSet<String> visiting = new LinkedHashSet<>();
        for (FeishuDepartmentPayload payload : payloads) {
            try {
                Department existing = existingByExternalId.get(payload.externalId());
                Department target = resolveDepartment(payload, payloadByExternalId, existingByExternalId, existingByCode, plannedByExternalId, visiting);
                List<ChangeItem> conflicts = new ArrayList<>();
                conflictDetector.detectDepartmentConflict(payload, existing, existingByCode.get(payload.departmentCode()), conflicts);
                conflicts.forEach(batch::addChangeItem);
                if (existing == null) {
                    batch.addChangeItem(createDepartmentCreateItem(target));
                } else {
                    diffPolicyService.buildDepartmentUpdateItems(
                        DepartmentImportSnapshot.from(existing),
                        DepartmentImportSnapshot.from(target)
                    ).forEach(batch::addChangeItem);
                }
            } catch (BizException exception) {
                batch.addChangeItem(blocker(TargetType.DEPARTMENT, payload.departmentCode(), exception.getMessage()));
            }
        }
        return plannedByExternalId;
    }

    private Department resolveDepartment(
        FeishuDepartmentPayload payload,
        Map<String, FeishuDepartmentPayload> payloadByExternalId,
        Map<String, Department> existingByExternalId,
        Map<String, Department> existingByCode,
        Map<String, Department> plannedByExternalId,
        LinkedHashSet<String> visiting
    ) {
        Department cached = plannedByExternalId.get(payload.externalId());
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(payload.externalId())) {
            throw new BizException("FEISHU_DEPT_TREE_INVALID", "部门树存在循环引用");
        }
        Department parent = null;
        if (payload.parentExternalId() != null && !payload.parentExternalId().isBlank()) {
            FeishuDepartmentPayload parentPayload = payloadByExternalId.get(payload.parentExternalId());
            parent = parentPayload == null
                ? existingByExternalId.get(payload.parentExternalId())
                : resolveDepartment(parentPayload, payloadByExternalId, existingByExternalId, existingByCode, plannedByExternalId, visiting);
            if (parent == null) {
                throw new BizException("FEISHU_DEPT_PARENT_NOT_FOUND", "部门父节点无法匹配");
            }
        }
        Department existing = existingByExternalId.get(payload.externalId());
        Department fallback = existingByCode.get(payload.departmentCode());
        Department base = existing != null ? existing : fallback;
        String ancestorPath = parent == null ? "/" + payload.departmentCode() : parent.getAncestorPath() + "/" + payload.departmentCode();
        int deptLevel = parent == null ? 1 : parent.getDeptLevel() + 1;
        Department target = Department.builder()
            .id(base == null ? null : base.getId())
            .deptCode(payload.departmentCode())
            .deptName(payload.departmentName())
            .parentDeptCode(parent == null ? null : parent.getDeptCode())
            .ancestorPath(ancestorPath)
            .deptLevel(deptLevel)
            .sourceType(SourceType.FEISHU)
            .externalId(payload.externalId())
            .ldapDn(base == null ? null : base.getLdapDn())
            .status(payload.status() == null ? 1 : payload.status())
            .build();
        plannedByExternalId.put(payload.externalId(), target);
        visiting.remove(payload.externalId());
        return target;
    }

    private void generateUserItems(List<FeishuUserPayload> payloads, ImportBatch batch, Map<String, Department> plannedDepartments) {
        Map<String, Department> departmentByExternalId = new HashMap<>();
        for (Department department : departmentRepository.findAll()) {
            if (department.getExternalId() != null && !department.getExternalId().isBlank()) {
                departmentByExternalId.put(department.getExternalId(), department);
            }
        }
        departmentByExternalId.putAll(plannedDepartments);
        Map<LeaderMatchKey, String> leaderRefs = buildImportedLeaderRefs(payloads);
        for (FeishuUserPayload payload : payloads) {
            try {
                User existingByUserId = userRepository.findByUserId(require(payload.userId(), "飞书 user_id 不能为空")).orElse(null);
                User existingByEmployeeNo = userRepository.findByEmployeeNo(payload.employeeNo()).orElse(null);
                List<ChangeItem> conflicts = new ArrayList<>();
                conflictDetector.detectUserConflict(payload, existingByUserId, existingByEmployeeNo, conflicts);
                conflicts.forEach(batch::addChangeItem);
                User target = buildUserTarget(payload, existingByUserId, departmentByExternalId, leaderRefs);
                if (existingByUserId == null) {
                    batch.addChangeItem(createUserCreateItem(target));
                } else {
                    diffPolicyService.buildUserUpdateItems(
                        UserImportSnapshot.from(existingByUserId),
                        UserImportSnapshot.from(target)
                    ).forEach(batch::addChangeItem);
                }
            } catch (BizException exception) {
                batch.addChangeItem(blocker(TargetType.USER, payload.userId(), exception.getMessage()));
            }
        }
    }

    private User buildUserTarget(
        FeishuUserPayload payload,
        User existing,
        Map<String, Department> departmentByExternalId,
        Map<LeaderMatchKey, String> leaderRefs
    ) {
        Department mainDepartment = departmentByExternalId.get(payload.mainDepartmentExternalId());
        if (mainDepartment == null) {
            throw new BizException("FEISHU_USER_DEPT_NOT_FOUND", "用户主部门无法匹配");
        }
        List<String> partTimeDeptCodes = new ArrayList<>();
        if (payload.partTimeDepartmentExternalIds() != null) {
            for (String externalId : payload.partTimeDepartmentExternalIds()) {
                Department department = departmentByExternalId.get(externalId);
                if (department == null) {
                    throw new BizException("FEISHU_USER_DEPT_NOT_FOUND", "用户兼职部门无法匹配");
                }
                partTimeDeptCodes.add(department.getDeptCode());
            }
        }
        String userId = existing == null ? payload.userId() : existing.getUserId();
        return User.builder()
            .id(existing == null ? null : existing.getId())
            .userId(userId)
            .realName(payload.realName())
            .email(preserve(payload.email(), existing == null ? null : existing.getEmail()))
            .intranetEmail(resolveIntranetEmail(payload, existing))
            .mobile(preserve(payload.mobile(), existing == null ? null : existing.getMobile()))
            .employeeNo(payload.employeeNo())
            .deptCode(mainDepartment.getDeptCode())
            .jobTitle(blankToNull(payload.jobTitle()))
            .directLeaderRaw(blankToNull(payload.directLeaderRaw()))
            .leaderRef(resolveLeaderRef(payload, existing, leaderRefs))
            .accountStatus(blankToNull(payload.accountStatus()))
            .partTimeDeptCodes(partTimeDeptCodes)
            .status(UserStatus.fromCode(payload.status()))
            .employmentStatus(UserStatus.fromCode(payload.status()) == UserStatus.DISABLED ? EmploymentStatus.RESIGNED : EmploymentStatus.ACTIVE)
            .sourceType(SourceType.FEISHU)
            .ldapDn(existing == null ? null : existing.getLdapDn())
            .tokenVersion(existing == null ? 0 : existing.getTokenVersion())
            .roleCodes(existing == null ? java.util.Set.of() : existing.getRoleCodes())
            .build();
    }

    private ChangeItem createDepartmentCreateItem(Department target) {
        return createObjectItem(TargetType.DEPARTMENT, target.getDeptCode(), DepartmentImportSnapshot.from(target));
    }

    private ChangeItem createUserCreateItem(User target) {
        return createObjectItem(TargetType.USER, target.getUserId(), UserImportSnapshot.from(target));
    }

    private List<ChangeItem> generateResignItems(List<FeishuUserPayload> payloads) {
        Set<String> importedUserIds = payloads.stream()
            .map(FeishuUserPayload::userId)
            .filter(userId -> userId != null && !userId.isBlank())
            .map(String::trim)
            .collect(Collectors.toSet());
        return userRepository.findAll().stream()
            .filter(user -> user.getSourceType() == SourceType.FEISHU)
            .filter(user -> user.getStatus() == UserStatus.ENABLED)
            .filter(user -> user.getEmploymentStatus() != EmploymentStatus.RESIGNED)
            .filter(user -> !importedUserIds.contains(user.getUserId()))
            .map(this::createUserResignItem)
            .toList();
    }

    private ChangeItem createUserResignItem(User current) {
        User resigned = current.toBuilder()
            .status(UserStatus.DISABLED)
            .employmentStatus(EmploymentStatus.RESIGNED)
            .accountStatus("离职")
            .build();
        return ChangeItem.builder()
            .targetType(TargetType.USER)
            .targetKey(current.getUserId())
            .changeType(ChangeType.RESIGN)
            .beforeValue("ACTIVE")
            .afterValue("RESIGNED")
            .beforeJson(jsonService.toJson(UserImportSnapshot.from(current)))
            .afterJson(jsonService.toJson(UserImportSnapshot.from(resigned)))
            .defaultEnabled(true)
            .enabled(true)
            .requiresConfirmation(true)
            .confirmed(false)
            .riskLevel(RiskLevel.HIGH)
            .status(ChangeItemStatus.PENDING)
            .retryCount(0)
            .build();
    }

    private ChangeItem createObjectItem(TargetType targetType, String targetKey, Object afterSnapshot) {
        return ChangeItem.builder()
            .targetType(targetType)
            .targetKey(targetKey)
            .changeType(ChangeType.CREATE)
            .afterJson(jsonService.toJson(afterSnapshot))
            .defaultEnabled(true)
            .enabled(true)
            .requiresConfirmation(false)
            .confirmed(true)
            .riskLevel(RiskLevel.MEDIUM)
            .status(ChangeItemStatus.PENDING)
            .retryCount(0)
            .build();
    }

    private ChangeItem blocker(TargetType targetType, String targetKey, String reason) {
        return ChangeItem.builder()
            .targetType(targetType)
            .targetKey(targetKey)
            .changeType(ChangeType.CONFLICT)
            .defaultEnabled(false)
            .enabled(false)
            .requiresConfirmation(true)
            .confirmed(false)
            .riskLevel(RiskLevel.BLOCKER)
            .blockReason(reason)
            .status(ChangeItemStatus.PENDING)
            .retryCount(0)
            .build();
    }

    private Map<LeaderMatchKey, String> buildImportedLeaderRefs(List<FeishuUserPayload> users) {
        Map<LeaderMatchKey, String> result = new HashMap<>();
        for (FeishuUserPayload payload : users) {
            result.put(new LeaderMatchKey(payload.realName(), payload.employeeNo()), payload.userId());
        }
        return result;
    }

    private String resolveLeaderRef(FeishuUserPayload payload, User existing, Map<LeaderMatchKey, String> leaderRefs) {
        String raw = payload.directLeaderRaw();
        if (raw == null || raw.isBlank()) {
            return existing == null ? null : existing.getLeaderRef();
        }
        String imported = leaderRefs.entrySet().stream()
            .filter(entry -> raw.trim().contains(entry.getKey().realName()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
        return imported == null && existing != null ? existing.getLeaderRef() : imported;
    }

    private String resolveIntranetEmail(FeishuUserPayload payload, User existing) {
        if (existing != null && existing.getIntranetEmail() != null && !existing.getIntranetEmail().isBlank()) {
            return existing.getIntranetEmail();
        }
        return intranetEmailGenerationService.generate(payload.userId(), existing == null ? null : existing.getId());
    }

    private String preserve(String incoming, String existing) {
        return incoming == null || incoming.isBlank() ? existing : incoming;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException("IMPORT_REQUIRED_FIELD_MISSING", message);
        }
        return value.trim();
    }

    private String buildBatchCode() {
        return "FEISHU_IMPORT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String sha256(String documentPath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Path path = Paths.get(documentPath);
            try (InputStream inputStream = Files.newInputStream(path);
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                digestInputStream.transferTo(java.io.OutputStream.nullOutputStream());
            }
            StringBuilder builder = new StringBuilder();
            for (byte item : digest.digest()) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new BizException("IMPORT_FILE_HASH_FAILED", "导入文件哈希计算失败");
        }
    }

    private record LeaderMatchKey(String realName, String employeeNo) {
    }
}
