package com.company.idm.application.sync.feishu;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.application.user.IntranetEmailGenerationService;
import com.company.idm.application.user.UsernameGenerationService;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncTargetType;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.user.PasswordPolicyValidator;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.AppLdapProperties;
import com.company.idm.infrastructure.feishu.FeishuDepartmentRemoteService;
import com.company.idm.infrastructure.feishu.FeishuUserRemoteService;
import com.company.idm.infrastructure.ldap.LdapDnHelper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Provide FEISHU user import diff calculation and execution.
 */
@Service
public class FeishuUserImportService {

    private static final String DEFAULT_IMPORTED_PASSWORD = "123456";
    private static final String NORMAL_USER_ROLE_CODE = "NORMAL_USER";
    private static final String USER_FILE_IMPORT_REQUIRES_DEPARTMENT_FILE_CODE = "FEISHU_USER_IMPORT_DEPARTMENT_FILE_REQUIRED";
    private static final String USER_FILE_IMPORT_REQUIRES_DEPARTMENT_FILE_MESSAGE = "请先导入部门文件后再导入用户文件";

    private final FeishuUserRemoteService userRemoteService;
    private final FeishuDepartmentRemoteService departmentRemoteService;
    private final FeishuImportDocumentResolver importDocumentResolver;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final LdapDirectoryService ldapDirectoryService;
    private final LdapGroupService ldapGroupService;
    private final PolicyRefreshService policyRefreshService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final IntranetEmailGenerationService intranetEmailGenerationService;
    private final AppLdapProperties ldapProperties;

    @Autowired
    public FeishuUserImportService(
        FeishuUserRemoteService userRemoteService,
        FeishuDepartmentRemoteService departmentRemoteService,
        FeishuImportDocumentResolver importDocumentResolver,
        UserRepository userRepository,
        DepartmentRepository departmentRepository,
        RoleRepository roleRepository,
        LdapDirectoryService ldapDirectoryService,
        LdapGroupService ldapGroupService,
        PolicyRefreshService policyRefreshService,
        PasswordPolicyValidator passwordPolicyValidator,
        IntranetEmailGenerationService intranetEmailGenerationService,
        AppLdapProperties ldapProperties
    ) {
        this.userRemoteService = userRemoteService;
        this.departmentRemoteService = departmentRemoteService;
        this.importDocumentResolver = importDocumentResolver;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.ldapDirectoryService = ldapDirectoryService;
        this.ldapGroupService = ldapGroupService;
        this.policyRefreshService = policyRefreshService;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.intranetEmailGenerationService = intranetEmailGenerationService;
        this.ldapProperties = ldapProperties;
    }

    public FeishuUserImportService(
        FeishuUserRemoteService userRemoteService,
        FeishuDepartmentRemoteService departmentRemoteService,
        FeishuImportDocumentResolver importDocumentResolver,
        UserRepository userRepository,
        DepartmentRepository departmentRepository,
        RoleRepository roleRepository,
        LdapDirectoryService ldapDirectoryService,
        LdapGroupService ldapGroupService,
        PolicyRefreshService policyRefreshService,
        PasswordPolicyValidator passwordPolicyValidator,
        UsernameGenerationService ignoredUsernameGenerationService,
        IntranetEmailGenerationService intranetEmailGenerationService,
        AppLdapProperties ldapProperties
    ) {
        this(
            userRemoteService,
            departmentRemoteService,
            importDocumentResolver,
            userRepository,
            departmentRepository,
            roleRepository,
            ldapDirectoryService,
            ldapGroupService,
            policyRefreshService,
            passwordPolicyValidator,
            intranetEmailGenerationService,
            ldapProperties
        );
    }

    public FeishuUserImportResult preview(SyncRequestPayload payload) {
        ImportPlan plan = buildPlan(userRemoteService.fetchUsers(), true);
        return new FeishuUserImportResult(plan.newCount, plan.updateCount, plan.noChangeCount, plan.diffs);
    }

    public FeishuUserImportResult execute(SyncRequestPayload payload) {
        ImportPlan plan = buildPlan(userRemoteService.fetchUsers(), true);
        return executePlan(plan);
    }

    public FeishuUserImportResult previewFromDocument(String documentPath) {
        ImportPlan plan = buildPlan(importDocumentResolver.resolveUsers(documentPath), false);
        return new FeishuUserImportResult(plan.newCount, plan.updateCount, plan.noChangeCount, plan.diffs);
    }

    public FeishuUserImportResult executeFromDocument(String documentPath) {
        ImportPlan plan = buildPlan(importDocumentResolver.resolveUsers(documentPath), false);
        return executePlan(plan);
    }

    public FeishuUserImportResult executeFromPayloads(List<FeishuUserPayload> users) {
        return executePlan(buildPlan(users, false));
    }

    private FeishuUserImportResult executePlan(ImportPlan plan) {
        passwordPolicyValidator.validate(DEFAULT_IMPORTED_PASSWORD);
        Role normalUserRole = roleRepository.findByCode(NORMAL_USER_ROLE_CODE)
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "普通用户角色不存在"));

        boolean roleBindingChanged = false;
        for (PlanItem item : plan.items) {
            if (item.changeType() == ChangeType.NO_CHANGE && !item.ldapRepairRequired()) {
                continue;
            }
            User saved = userRepository.save(item.target());
            String ldapDn = syncLdapUser(saved, item.departments());
            if (!safe(ldapDn).equals(safe(saved.getLdapDn()))) {
                saved = userRepository.save(saved.toBuilder().ldapDn(ldapDn).build());
            }
            if (shouldAssignDefaultRole(saved, item.existing())) {
                userRepository.assignRoles(saved.getId(), List.of(normalUserRole.getId()));
                roleBindingChanged = true;
            }
        }

        if (roleBindingChanged) {
            policyRefreshService.refresh();
        }
        return new FeishuUserImportResult(plan.newCount, plan.updateCount, plan.noChangeCount, List.of());
    }

    private boolean shouldAssignDefaultRole(User saved, User existing) {
        if (existing == null) {
            return true;
        }
        Set<String> currentRoles = existing.getRoleCodes();
        return currentRoles == null || currentRoles.isEmpty();
    }

    private String syncLdapUser(User saved, List<Department> departments) {
        boolean existsInLdap = ldapDirectoryService.existsByUid(saved.getUserId());
        String ldapDn;
        if (!existsInLdap) {
            ldapDn = ldapDirectoryService.createUser(saved, DEFAULT_IMPORTED_PASSWORD);
        } else {
            ldapDirectoryService.updateUser(saved);
            ldapDn = buildUserDn(saved.getUserId());
        }
        if (saved.getStatus() == UserStatus.ENABLED) {
            ldapDirectoryService.enableUser(saved.getUserId());
        } else {
            ldapDirectoryService.disableUser(saved.getUserId());
        }
        if (departments.isEmpty()) {
            ldapGroupService.removeUserFromAllGroups(saved.getUserId());
            return ldapDn;
        }
        for (Department department : departments) {
            String groupDn = ldapGroupService.createGroup(department.getDeptCode(), department.getDeptName());
            updateDepartmentLdapDn(department, groupDn);
        }
        ldapGroupService.syncUserGroups(
            saved.getUserId(),
            departments.stream().map(Department::getDeptCode).toList()
        );
        return ldapDn;
    }

    private void updateDepartmentLdapDn(Department department, String ldapDn) {
        if (department == null || ldapDn == null || ldapDn.isBlank()) {
            return;
        }
        if (ldapDn.equals(department.getLdapDn())) {
            return;
        }
        departmentRepository.save(department.toBuilder().ldapDn(ldapDn).build());
    }

    private ImportPlan buildPlan(List<FeishuUserPayload> users, boolean includeRemoteDepartmentFallback) {
        validateDuplicates(users);
        Map<String, Department> departmentByExternalId = buildDepartmentIndex(includeRemoteDepartmentFallback);
        Map<LeaderMatchKey, String> importedLeaderRefByKey = buildImportedLeaderRefIndex(users);

        List<PlanItem> items = new ArrayList<>();
        List<SyncDiffPayload> allDiffs = new ArrayList<>();
        int newCount = 0;
        int updateCount = 0;
        int noChangeCount = 0;

        for (FeishuUserPayload payloadItem : users) {
            DepartmentAssignment departmentAssignment = resolveDepartmentAssignment(payloadItem, departmentByExternalId, includeRemoteDepartmentFallback);
            User existing = resolveExistingUser(payloadItem);
            User target = buildTargetUser(payloadItem, departmentAssignment, existing, importedLeaderRefByKey);
            ChangeType changeType = resolveChangeType(existing, target);
            boolean ldapRepairRequired = target.getLdapDn() == null
                || !ldapDirectoryService.existsByUid(target.getUserId())
                || !safe(target.getLdapDn()).equals(safe(buildUserDn(target.getUserId())));

            if (changeType == ChangeType.NEW) {
                newCount++;
            } else if (changeType == ChangeType.NO_CHANGE && !ldapRepairRequired) {
                noChangeCount++;
            } else {
                updateCount++;
            }

            List<SyncDiffPayload> diffs = buildDiffs(existing, target, changeType, ldapRepairRequired);
            allDiffs.addAll(diffs);
            items.add(new PlanItem(existing, target, departmentAssignment.departments(), changeType, ldapRepairRequired));
        }

        return new ImportPlan(items, allDiffs, newCount, updateCount, noChangeCount);
    }

    private Map<String, Department> buildDepartmentIndex(boolean includeRemoteDepartmentFallback) {
        Map<String, Department> departmentByExternalId = new LinkedHashMap<>();
        for (Department department : departmentRepository.findAll()) {
            if (department.getExternalId() != null && !department.getExternalId().isBlank()) {
                departmentByExternalId.put(department.getExternalId(), department);
            }
        }
        if (includeRemoteDepartmentFallback) {
            for (FeishuDepartmentPayload payload : departmentRemoteService.fetchDepartments()) {
                departmentByExternalId.putIfAbsent(payload.externalId(), Department.builder()
                    .deptCode(payload.departmentCode())
                    .deptName(payload.departmentName())
                    .externalId(payload.externalId())
                    .sourceType(SourceType.FEISHU)
                    .status(payload.status() == null ? 1 : payload.status())
                    .build());
            }
        }
        return departmentByExternalId;
    }

    private DepartmentAssignment resolveDepartmentAssignment(
        FeishuUserPayload payload,
        Map<String, Department> departmentByExternalId,
        boolean includeRemoteDepartmentFallback
    ) {
        Department mainDepartment = departmentByExternalId.get(payload.mainDepartmentExternalId());
        if (mainDepartment == null) {
            if (!includeRemoteDepartmentFallback) {
                throw new BizException(
                    USER_FILE_IMPORT_REQUIRES_DEPARTMENT_FILE_CODE,
                    USER_FILE_IMPORT_REQUIRES_DEPARTMENT_FILE_MESSAGE
                );
            }
            throw new BizException("FEISHU_USER_DEPT_NOT_FOUND", "飞书用户部门不存在");
        }

        LinkedHashSet<String> partTimeDepartmentExternalIds = new LinkedHashSet<>();
        if (payload.partTimeDepartmentExternalIds() != null) {
            for (String externalId : payload.partTimeDepartmentExternalIds()) {
                if (externalId == null || externalId.isBlank() || externalId.equals(payload.mainDepartmentExternalId())) {
                    continue;
                }
                partTimeDepartmentExternalIds.add(externalId);
            }
        }

        List<Department> departments = new ArrayList<>();
        departments.add(mainDepartment);
        List<String> partTimeDeptCodes = new ArrayList<>();
        for (String externalId : partTimeDepartmentExternalIds) {
            Department partTimeDepartment = departmentByExternalId.get(externalId);
            if (partTimeDepartment == null) {
                if (!includeRemoteDepartmentFallback) {
                    throw new BizException(
                        USER_FILE_IMPORT_REQUIRES_DEPARTMENT_FILE_CODE,
                        USER_FILE_IMPORT_REQUIRES_DEPARTMENT_FILE_MESSAGE
                    );
                }
                throw new BizException("FEISHU_USER_DEPT_NOT_FOUND", "飞书用户部门不存在");
            }
            departments.add(partTimeDepartment);
            partTimeDeptCodes.add(partTimeDepartment.getDeptCode());
        }
        return new DepartmentAssignment(mainDepartment, List.copyOf(partTimeDeptCodes), List.copyOf(departments));
    }

    private User resolveExistingUser(FeishuUserPayload payload) {
        String expectedUserId = requireUserId(payload.userId());
        User byEmployeeNo = userRepository.findByEmployeeNo(payload.employeeNo()).orElse(null);
        User byUserId = userRepository.findByUserId(expectedUserId).orElse(null);

        if (byEmployeeNo != null && byUserId != null && !isSameUser(byEmployeeNo, byUserId)) {
            throw new BizException("FEISHU_USER_IDENTITY_CONFLICT", "飞书用户 user_id 与 employee_no 映射到了不同用户");
        }

        if (byUserId != null) {
            return byUserId;
        }
        if (byEmployeeNo != null) {
            if (byEmployeeNo.getSourceType() == SourceType.MANUAL) {
                throw new BizException("FEISHU_USER_SOURCE_CONFLICT", "飞书用户与手工用户身份冲突");
            }
            return byEmployeeNo;
        }
        return null;
    }

    private User buildTargetUser(
        FeishuUserPayload payload,
        DepartmentAssignment assignment,
        User existing,
        Map<LeaderMatchKey, String> importedLeaderRefByKey
    ) {
        String userId = existing == null ? requireUserId(payload.userId()) : existing.getUserId();
        String preservedEmail = preserveExistingOptionalValue(payload.email(), existing == null ? null : existing.getEmail());
        String preservedMobile = preserveExistingOptionalValue(payload.mobile(), existing == null ? null : existing.getMobile());
        String intranetEmail = resolveIntranetEmail(payload, existing);
        String leaderRef = resolveLeaderRef(payload, existing, importedLeaderRefByKey);
        return User.builder()
            .id(existing == null ? null : existing.getId())
            .userId(userId)
            .realName(payload.realName())
            .email(preservedEmail)
            .intranetEmail(intranetEmail)
            .mobile(preservedMobile)
            .employeeNo(blankToNull(payload.employeeNo()))
            .deptCode(assignment.mainDepartment().getDeptCode())
            .jobTitle(blankToNull(payload.jobTitle()))
            .directLeaderRaw(blankToNull(payload.directLeaderRaw()))
            .leaderRef(leaderRef)
            .accountStatus(resolveAccountStatus(payload, existing))
            .partTimeDeptCodes(assignment.partTimeDeptCodes())
            .status(normalizeStatus(payload.status()))
            .employmentStatus(resolveEmploymentStatus(payload, existing))
            .sourceType(SourceType.FEISHU)
            .ldapDn(existing == null ? buildUserDn(userId) : existing.getLdapDn())
            .tokenVersion(existing == null ? 0 : existing.getTokenVersion())
            .roleCodes(existing == null ? Set.of() : existing.getRoleCodes())
            .build();
    }

    private ChangeType resolveChangeType(User existing, User target) {
        if (existing == null) {
            return ChangeType.NEW;
        }
        boolean sameBasic = safe(existing.getRealName()).equals(safe(target.getRealName()))
            && safe(existing.getEmail()).equals(safe(target.getEmail()))
            && safe(existing.getIntranetEmail()).equals(safe(target.getIntranetEmail()))
            && safe(existing.getMobile()).equals(safe(target.getMobile()))
            && safe(existing.getEmployeeNo()).equals(safe(target.getEmployeeNo()))
            && safe(existing.getDeptCode()).equals(safe(target.getDeptCode()))
            && safe(existing.getJobTitle()).equals(safe(target.getJobTitle()))
            && safe(existing.getDirectLeaderRaw()).equals(safe(target.getDirectLeaderRaw()))
            && safe(existing.getLeaderRef()).equals(safe(target.getLeaderRef()))
            && safe(existing.getAccountStatus()).equals(safe(target.getAccountStatus()))
            && normalizeDeptCodes(existing.getPartTimeDeptCodes()).equals(normalizeDeptCodes(target.getPartTimeDeptCodes()))
            && existing.getEmploymentStatus() == target.getEmploymentStatus()
            && existing.getStatus() == target.getStatus();
        return sameBasic ? ChangeType.NO_CHANGE : ChangeType.UPDATE;
    }

    private List<SyncDiffPayload> buildDiffs(User existing, User target, ChangeType changeType, boolean ldapRepairRequired) {
        List<SyncDiffPayload> diffs = new ArrayList<>();
        if (changeType == ChangeType.NEW) {
            diffs.add(new SyncDiffPayload(
                SyncTargetType.USER,
                target.getUserId(),
                SyncDiffType.MISSING_IN_MYSQL,
                snapshotTarget(target),
                null,
                true
            ));
            return diffs;
        }
        if (changeType == ChangeType.UPDATE) {
            boolean deptChanged = !safe(existing.getDeptCode()).equals(safe(target.getDeptCode()))
                || !normalizeDeptCodes(existing.getPartTimeDeptCodes()).equals(normalizeDeptCodes(target.getPartTimeDeptCodes()));
            diffs.add(new SyncDiffPayload(
                SyncTargetType.USER,
                target.getUserId(),
                deptChanged ? SyncDiffType.RELATION_MISMATCH : SyncDiffType.FIELD_MISMATCH,
                snapshotTarget(target),
                snapshotExisting(existing),
                true
            ));
        } else if (ldapRepairRequired) {
            diffs.add(new SyncDiffPayload(
                SyncTargetType.USER,
                target.getUserId(),
                SyncDiffType.MISSING_IN_LDAP,
                snapshotTarget(target),
                snapshotExisting(existing),
                true
            ));
        }
        return diffs;
    }

    private void validateDuplicates(List<FeishuUserPayload> users) {
        Map<String, Integer> userIdCounter = new LinkedHashMap<>();
        Map<String, Integer> employeeNoCounter = new LinkedHashMap<>();
        for (FeishuUserPayload payload : users) {
            userIdCounter.merge(requireUserId(payload.userId()), 1, Integer::sum);
            if (payload.employeeNo() == null || payload.employeeNo().isBlank()) {
                throw new BizException("FEISHU_USER_EMPLOYEE_NO_REQUIRED", "飞书用户 employee_no 不能为空");
            }
            employeeNoCounter.merge(payload.employeeNo(), 1, Integer::sum);
        }
        userIdCounter.forEach((userId, count) -> {
            if (count > 1) {
                throw new BizException("FEISHU_USER_DUPLICATE_USER_ID", "飞书用户 user_id 重复");
            }
        });
        employeeNoCounter.forEach((employeeNo, count) -> {
            if (count > 1) {
                throw new BizException("FEISHU_USER_DUPLICATE_EMPLOYEE_NO", "飞书用户 employee_no 重复");
            }
        });
    }

    private List<String> normalizeDeptCodes(List<String> deptCodes) {
        if (deptCodes == null || deptCodes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalizedCodes = new LinkedHashSet<>();
        for (String deptCode : deptCodes) {
            if (deptCode == null || deptCode.isBlank()) {
                continue;
            }
            normalizedCodes.add(deptCode.trim());
        }
        return List.copyOf(normalizedCodes);
    }

    private UserStatus normalizeStatus(Integer status) {
        return status == null ? UserStatus.ENABLED : UserStatus.fromCode(status);
    }

    private EmploymentStatus resolveEmploymentStatus(FeishuUserPayload payload, User existing) {
        if (payload == null) {
            return existing == null || existing.getEmploymentStatus() == null ? EmploymentStatus.ACTIVE : existing.getEmploymentStatus();
        }
        return normalizeStatus(payload.status()) == UserStatus.ENABLED ? EmploymentStatus.ACTIVE : EmploymentStatus.RESIGNED;
    }

    private String resolveAccountStatus(FeishuUserPayload payload, User existing) {
        String importedAccountStatus = blankToNull(payload.accountStatus());
        if (importedAccountStatus != null) {
            return importedAccountStatus;
        }
        if (existing != null && existing.getAccountStatus() != null && !existing.getAccountStatus().isBlank()) {
            return existing.getAccountStatus();
        }
        return normalizeStatus(payload.status()) == UserStatus.ENABLED ? "正常" : "冻结";
    }

    private String resolveIntranetEmail(FeishuUserPayload payload, User existing) {
        if (existing != null && existing.getIntranetEmail() != null && !existing.getIntranetEmail().isBlank()) {
            return existing.getIntranetEmail();
        }
        return intranetEmailGenerationService.generate(requireUserId(payload.userId()), existing == null ? null : existing.getId());
    }

    private String resolveLeaderRef(FeishuUserPayload payload, User existing, Map<LeaderMatchKey, String> importedLeaderRefByKey) {
        String directLeaderRaw = blankToNull(payload.directLeaderRaw());
        if (directLeaderRaw == null) {
            return existing == null ? null : existing.getLeaderRef();
        }
        ParsedLeader parsedLeader = parseLeader(directLeaderRaw);
        if (parsedLeader == null) {
            return existing == null ? null : existing.getLeaderRef();
        }
        String importedLeaderRef = importedLeaderRefByKey.get(new LeaderMatchKey(parsedLeader.realName(), parsedLeader.mobile()));
        if (importedLeaderRef != null && !importedLeaderRef.isBlank()) {
            return importedLeaderRef;
        }
        for (User candidate : userRepository.findAll()) {
            if (candidate.getRealName() == null || candidate.getMobile() == null || candidate.getEmployeeNo() == null) {
                continue;
            }
            if (candidate.getRealName().equals(parsedLeader.realName()) && candidate.getMobile().equals(parsedLeader.mobile())) {
                return candidate.getEmployeeNo();
            }
        }
        return null;
    }

    private Map<LeaderMatchKey, String> buildImportedLeaderRefIndex(List<FeishuUserPayload> users) {
        Map<LeaderMatchKey, String> result = new LinkedHashMap<>();
        for (FeishuUserPayload payload : users) {
            String realName = blankToNull(payload.realName());
            String mobile = blankToNull(payload.mobile());
            String employeeNo = blankToNull(payload.employeeNo());
            if (realName == null || mobile == null || employeeNo == null) {
                continue;
            }
            String normalizedMobile = mobile.replaceAll("\\D", "");
            if (normalizedMobile.length() == 13 && normalizedMobile.startsWith("86")) {
                normalizedMobile = normalizedMobile.substring(2);
            }
            if (normalizedMobile.isBlank()) {
                continue;
            }
            result.putIfAbsent(new LeaderMatchKey(realName, normalizedMobile), employeeNo);
        }
        return result;
    }

    private ParsedLeader parseLeader(String directLeaderRaw) {
        if (directLeaderRaw == null || directLeaderRaw.isBlank()) {
            return null;
        }
        int leftBracketIndex = directLeaderRaw.lastIndexOf('(');
        int rightBracketIndex = directLeaderRaw.lastIndexOf(')');
        if (leftBracketIndex <= 0 || rightBracketIndex <= leftBracketIndex) {
            return null;
        }
        String realName = directLeaderRaw.substring(0, leftBracketIndex).trim();
        String mobile = directLeaderRaw.substring(leftBracketIndex + 1, rightBracketIndex).trim();
        if (realName.isBlank() || mobile.isBlank()) {
            return null;
        }
        String normalizedMobile = mobile.replaceAll("\\D", "");
        if (normalizedMobile.length() == 13 && normalizedMobile.startsWith("86")) {
            normalizedMobile = normalizedMobile.substring(2);
        }
        return normalizedMobile.isBlank() ? null : new ParsedLeader(realName, normalizedMobile);
    }

    private boolean isSameUser(User left, User right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return left.getUserId() != null && left.getUserId().equals(right.getUserId());
    }

    private String buildUserDn(String userId) {
        return LdapDnHelper.buildUserDn(ldapProperties, userId);
    }

    private String snapshotTarget(User user) {
        return """
            {"userId":"%s","realName":"%s","employeeNo":"%s","deptCode":"%s","partTimeDeptCodes":"%s","status":%s}
            """.formatted(
            safe(user.getUserId()),
            safe(user.getRealName()),
            safe(user.getEmployeeNo()),
            safe(user.getDeptCode()),
            safe(String.join(",", normalizeDeptCodes(user.getPartTimeDeptCodes()))),
            user.getStatus().getCode()
        );
    }

    private String snapshotExisting(User user) {
        return """
            {"userId":"%s","realName":"%s","employeeNo":"%s","deptCode":"%s","partTimeDeptCodes":"%s","status":%s,"ldapDn":"%s"}
            """.formatted(
            safe(user.getUserId()),
            safe(user.getRealName()),
            safe(user.getEmployeeNo()),
            safe(user.getDeptCode()),
            safe(String.join(",", normalizeDeptCodes(user.getPartTimeDeptCodes()))),
            user.getStatus().getCode(),
            safe(user.getLdapDn())
        );
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private String preserveExistingOptionalValue(String importedValue, String existingValue) {
        String normalizedImportedValue = blankToNull(importedValue);
        if (normalizedImportedValue != null) {
            return normalizedImportedValue;
        }
        return blankToNull(existingValue);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BizException("FEISHU_USER_ID_REQUIRED", "飞书用户 user_id 不能为空");
        }
        return userId.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    private record ImportPlan(
        List<PlanItem> items,
        List<SyncDiffPayload> diffs,
        int newCount,
        int updateCount,
        int noChangeCount
    ) {
    }

    private record PlanItem(
        User existing,
        User target,
        List<Department> departments,
        ChangeType changeType,
        boolean ldapRepairRequired
    ) {
    }

    private record DepartmentAssignment(
        Department mainDepartment,
        List<String> partTimeDeptCodes,
        List<Department> departments
    ) {
    }

    private record ParsedLeader(
        String realName,
        String mobile
    ) {
    }

    private record LeaderMatchKey(
        String realName,
        String mobile
    ) {
    }

    private enum ChangeType {
        NEW,
        UPDATE,
        NO_CHANGE
    }
}

