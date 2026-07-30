package com.company.idm.application.sync.importplan;

import com.company.idm.application.sync.feishu.FeishuFullImportDocument;
import com.company.idm.application.sync.feishu.FeishuImportDocumentResolver;
import com.company.idm.application.sync.feishu.FeishuUserPayload;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ImportBatch;
import com.company.idm.domain.sync.ImportConflictCode;
import com.company.idm.domain.sync.TargetType;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ImportConflictCandidateRecoveryService {

    private final FeishuImportDocumentResolver documentResolver;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final ImportUserTargetFactory userTargetFactory;
    private final ImportJsonService jsonService;

    public ImportConflictCandidateRecoveryService(
        FeishuImportDocumentResolver documentResolver,
        DepartmentRepository departmentRepository,
        UserRepository userRepository,
        ImportUserTargetFactory userTargetFactory,
        ImportJsonService jsonService
    ) {
        this.documentResolver = documentResolver;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.userTargetFactory = userTargetFactory;
        this.jsonService = jsonService;
    }

    public void restoreIfMissing(ImportBatch batch, ChangeItem conflict) {
        if (conflict.getAfterJson() != null && !conflict.getAfterJson().isBlank()) {
            return;
        }
        if (conflict.getConflictCode() != ImportConflictCode.EMPLOYEE_NO_OWNED_BY_ANOTHER_USER) {
            throw new BizException("IMPORT_CONFLICT_MERGE_NOT_ALLOWED", "当前冲突不允许恢复合并候选资料");
        }
        if (batch == null || batch.getFileHash() == null || batch.getFileHash().isBlank()) {
            throw new BizException("IMPORT_BATCH_SOURCE_HASH_MISSING", "导入批次缺少源文件哈希，无法恢复候选资料");
        }

        FeishuFullImportDocument document = documentResolver.resolveFullImportDocumentByHash(batch.getFileHash());
        FeishuUserPayload incoming = findUniqueIncomingUser(document.users(), conflict.getTargetKey());
        assertUnambiguousEmployeeNumber(document.users(), incoming);
        User existing = userRepository.findByEmployeeNo(incoming.employeeNo())
            .orElseThrow(() -> new BizException("IMPORT_CONFLICT_TARGET_NOT_FOUND", "工号对应的现有账号不存在"));
        assertNoCrossAccountMatch(document.users(), incoming, existing);
        userRepository.findByUserId(incoming.userId())
            .filter(user -> !sameUser(user, existing))
            .ifPresent(user -> {
                throw new BizException("IMPORT_CONFLICT_CANDIDATE_AMBIGUOUS", "导入用户同时命中另一个现有账号，不能自动合并");
            });

        Map<String, Department> departmentsByExternalId = resolveDepartments(batch);
        ImportUserTargetFactory.TargetContext context = userTargetFactory.createContext(
            departmentsByExternalId,
            document.users()
        );
        User target = userTargetFactory.build(incoming, existing, context);
        conflict.restoreMergeCandidateSnapshots(
            jsonService.toJson(UserImportSnapshot.from(existing)),
            jsonService.toJson(UserImportSnapshot.from(target))
        );
    }

    private FeishuUserPayload findUniqueIncomingUser(List<FeishuUserPayload> users, String targetKey) {
        List<FeishuUserPayload> matches = users.stream()
            .filter(user -> normalizedEquals(user.userId(), targetKey))
            .toList();
        if (matches.size() != 1) {
            throw new BizException("IMPORT_CONFLICT_CANDIDATE_AMBIGUOUS", "源文件中无法唯一匹配冲突用户，不能自动合并");
        }
        FeishuUserPayload incoming = matches.get(0);
        if (incoming.employeeNo() == null || incoming.employeeNo().isBlank()) {
            throw new BizException("IMPORT_CONFLICT_CANDIDATE_MISSING", "冲突候选用户缺少工号");
        }
        return incoming;
    }

    private void assertUnambiguousEmployeeNumber(List<FeishuUserPayload> users, FeishuUserPayload incoming) {
        long matches = users.stream()
            .filter(user -> normalizedEquals(user.employeeNo(), incoming.employeeNo()))
            .count();
        if (matches != 1) {
            throw new BizException("IMPORT_CONFLICT_CANDIDATE_AMBIGUOUS", "源文件中工号不唯一，不能自动合并");
        }
    }

    private void assertNoCrossAccountMatch(List<FeishuUserPayload> users, FeishuUserPayload incoming, User existing) {
        boolean crossMatch = users.stream()
            .filter(user -> user != incoming)
            .anyMatch(user -> normalizedEquals(user.userId(), existing.getUserId()));
        if (crossMatch) {
            throw new BizException("IMPORT_CONFLICT_CANDIDATE_AMBIGUOUS", "源文件中存在账号交叉命中，不能自动合并");
        }
    }

    private Map<String, Department> resolveDepartments(ImportBatch batch) {
        Map<String, Department> result = new LinkedHashMap<>();
        for (Department department : departmentRepository.findAll()) {
            putByExternalId(result, department);
        }
        for (ChangeItem item : batch.getChangeItems()) {
            if (item.getTargetType() != TargetType.DEPARTMENT || item.getAfterJson() == null || item.getAfterJson().isBlank()) {
                continue;
            }
            DepartmentImportSnapshot snapshot = jsonService.readDepartmentSnapshot(item.getAfterJson());
            if (snapshot != null) {
                putByExternalId(result, snapshot.toDepartment());
            }
        }
        return result;
    }

    private void putByExternalId(Map<String, Department> departments, Department department) {
        if (department.getExternalId() != null && !department.getExternalId().isBlank()) {
            departments.put(department.getExternalId(), department);
        }
    }

    private boolean sameUser(User left, User right) {
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return normalizedEquals(left.getUserId(), right.getUserId());
    }

    private boolean normalizedEquals(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }
}
