package com.company.idm.application.sync.feishu;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncTargetType;
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
import com.company.idm.infrastructure.ldap.LdapDnHelper;
import com.company.idm.infrastructure.feishu.FeishuDepartmentRemoteService;
import com.company.idm.infrastructure.feishu.FeishuUserRemoteService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 提供飞书用户导入的差异计算和执行能力。
 */
@Service
public class FeishuUserImportService {

    private static final String DEFAULT_IMPORTED_PASSWORD = "123456";
    private static final String NORMAL_USER_ROLE_CODE = "NORMAL_USER";
    private static final String USER_FILE_IMPORT_REQUIRES_DEPARTMENT_FILE_CODE = "FEISHU_USER_IMPORT_DEPARTMENT_FILE_REQUIRED";
    private static final String USER_FILE_IMPORT_REQUIRES_DEPARTMENT_FILE_MESSAGE = "请先更新部门文件后再导入用户文件";

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
    private final AppLdapProperties ldapProperties;

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
        this.ldapProperties = ldapProperties;
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
            String ldapDn = syncLdapUser(saved, item.department());
            if (!safe(ldapDn).equals(safe(saved.getLdapDn()))) {
                saved = userRepository.save(saved.toBuilder().ldapDn(ldapDn).build());
            }
            syncDepartmentMembership(item.existing(), saved, item.department());
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

    private String syncLdapUser(User saved, Department department) {
        boolean existsInLdap = ldapDirectoryService.existsByUid(saved.getUsername());
        String ldapDn;
        if (!existsInLdap) {
            ldapDn = ldapDirectoryService.createUser(saved, DEFAULT_IMPORTED_PASSWORD);
        } else {
            ldapDirectoryService.updateUser(saved);
            ldapDn = buildUserDn(saved.getUsername());
        }
        if (saved.getStatus() == UserStatus.ENABLED) {
            ldapDirectoryService.enableUser(saved.getUsername());
        } else {
            ldapDirectoryService.disableUser(saved.getUsername());
        }
        if (department != null) {
            String groupDn = ldapGroupService.createGroup(department.getDeptCode(), department.getDeptName());
            updateDepartmentLdapDn(department, groupDn);
        }
        return ldapDn;
    }

    private void syncDepartmentMembership(User existing, User saved, Department department) {
        String oldDeptCode = existing == null ? null : existing.getDeptCode();
        String newDeptCode = saved.getDeptCode();
        if (oldDeptCode != null && !oldDeptCode.isBlank() && !oldDeptCode.equals(newDeptCode)) {
            ldapGroupService.removeUserFromGroup(saved.getUsername(), oldDeptCode);
        }
        if (newDeptCode != null && !newDeptCode.isBlank()) {
            ldapGroupService.addUserToGroup(saved.getUsername(), newDeptCode);
        }
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

        List<PlanItem> items = new ArrayList<>();
        List<SyncDiffPayload> allDiffs = new ArrayList<>();
        int newCount = 0;
        int updateCount = 0;
        int noChangeCount = 0;

        for (FeishuUserPayload payloadItem : users) {
            Department department = resolveDepartment(payloadItem, departmentByExternalId, includeRemoteDepartmentFallback);
            User existing = resolveExistingUser(payloadItem);
            User target = buildTargetUser(payloadItem, department, existing);
            ChangeType changeType = resolveChangeType(existing, target);
            boolean ldapRepairRequired = target.getLdapDn() == null
                || !ldapDirectoryService.existsByUid(target.getUsername())
                || !safe(target.getLdapDn()).equals(safe(buildUserDn(target.getUsername())));

            if (changeType == ChangeType.NEW) {
                newCount++;
            } else if (changeType == ChangeType.NO_CHANGE && !ldapRepairRequired) {
                noChangeCount++;
            } else {
                updateCount++;
            }

            List<SyncDiffPayload> diffs = buildDiffs(existing, target, changeType, ldapRepairRequired);
            allDiffs.addAll(diffs);
            items.add(new PlanItem(existing, target, department, changeType, ldapRepairRequired));
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

    private Department resolveDepartment(
        FeishuUserPayload payload,
        Map<String, Department> departmentByExternalId,
        boolean includeRemoteDepartmentFallback
    ) {
        Department department = departmentByExternalId.get(payload.mainDepartmentExternalId());
        if (department == null) {
            if (!includeRemoteDepartmentFallback) {
                throw new BizException(
                    USER_FILE_IMPORT_REQUIRES_DEPARTMENT_FILE_CODE,
                    USER_FILE_IMPORT_REQUIRES_DEPARTMENT_FILE_MESSAGE
                );
            }
            throw new BizException("FEISHU_USER_DEPT_NOT_FOUND", "飞书用户主部门不存在");
        }
        return department;
    }

    private User resolveExistingUser(FeishuUserPayload payload) {
        User byExternalId = userRepository.findByExternalId(payload.externalId()).orElse(null);
        User byUsername = userRepository.findByUsername(payload.username()).orElse(null);
        User byEmployeeNo = userRepository.findByEmployeeNo(payload.employeeNo()).orElse(null);

        if (byExternalId != null && !byExternalId.getUsername().equals(payload.username())) {
            throw new BizException("FEISHU_USER_USERNAME_CONFLICT", "飞书用户 external_id 与 username 映射冲突");
        }
        if (byExternalId != null && payload.employeeNo() != null && !payload.employeeNo().isBlank()
            && byExternalId.getEmployeeNo() != null && !byExternalId.getEmployeeNo().equals(payload.employeeNo())) {
            throw new BizException("FEISHU_USER_EMPLOYEE_NO_CONFLICT", "飞书用户 external_id 与 employee_no 映射冲突");
        }

        if (byExternalId == null && byUsername != null) {
            if (byUsername.getSourceType() == SourceType.MANUAL) {
                throw new BizException("FEISHU_USER_SOURCE_CONFLICT", "飞书用户 username 与手工用户冲突");
            }
            if (byUsername.getExternalId() != null && !byUsername.getExternalId().isBlank()
                && !byUsername.getExternalId().equals(payload.externalId())) {
                throw new BizException("FEISHU_USER_DUPLICATE_USERNAME", "飞书用户 username 已被其他用户占用");
            }
            return byUsername;
        }

        if (byExternalId == null && byEmployeeNo != null) {
            if (!byEmployeeNo.getUsername().equals(payload.username())) {
                throw new BizException("FEISHU_USER_DUPLICATE_EMPLOYEE_NO", "飞书用户 employee_no 已被其他用户占用");
            }
            if (byEmployeeNo.getSourceType() == SourceType.MANUAL) {
                throw new BizException("FEISHU_USER_SOURCE_CONFLICT", "飞书用户 employee_no 与手工用户冲突");
            }
            if (byEmployeeNo.getExternalId() != null && !byEmployeeNo.getExternalId().isBlank()
                && !byEmployeeNo.getExternalId().equals(payload.externalId())) {
                throw new BizException("FEISHU_USER_DUPLICATE_EMPLOYEE_NO", "飞书用户 employee_no 已被其他用户占用");
            }
            return byEmployeeNo;
        }

        if (byExternalId == null && byUsername == null && byEmployeeNo == null) {
            return null;
        }
        return byExternalId;
    }

    private User buildTargetUser(FeishuUserPayload payload, Department department, User existing) {
        return User.builder()
            .id(existing == null ? null : existing.getId())
            .username(existing == null ? payload.username() : existing.getUsername())
            .realName(payload.realName())
            .email(blankToNull(payload.email()))
            .mobile(blankToNull(payload.mobile()))
            .employeeNo(blankToNull(payload.employeeNo()))
            .deptCode(department.getDeptCode())
            .status(normalizeStatus(payload.status()))
            .sourceType(SourceType.FEISHU)
            .externalId(payload.externalId())
            .ldapDn(existing == null ? buildUserDn(payload.username()) : existing.getLdapDn())
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
            && safe(existing.getMobile()).equals(safe(target.getMobile()))
            && safe(existing.getEmployeeNo()).equals(safe(target.getEmployeeNo()))
            && safe(existing.getDeptCode()).equals(safe(target.getDeptCode()))
            && existing.getStatus() == target.getStatus();
        return sameBasic ? ChangeType.NO_CHANGE : ChangeType.UPDATE;
    }

    private List<SyncDiffPayload> buildDiffs(User existing, User target, ChangeType changeType, boolean ldapRepairRequired) {
        List<SyncDiffPayload> diffs = new ArrayList<>();
        if (changeType == ChangeType.NEW) {
            diffs.add(new SyncDiffPayload(
                SyncTargetType.USER,
                target.getUsername(),
                SyncDiffType.MISSING_IN_MYSQL,
                snapshotTarget(target),
                null,
                true
            ));
            return diffs;
        }
        if (changeType == ChangeType.UPDATE) {
            boolean deptChanged = !safe(existing.getDeptCode()).equals(safe(target.getDeptCode()));
            diffs.add(new SyncDiffPayload(
                SyncTargetType.USER,
                target.getUsername(),
                deptChanged ? SyncDiffType.RELATION_MISMATCH : SyncDiffType.FIELD_MISMATCH,
                snapshotTarget(target),
                snapshotExisting(existing),
                true
            ));
        } else if (ldapRepairRequired) {
            diffs.add(new SyncDiffPayload(
                SyncTargetType.USER,
                target.getUsername(),
                SyncDiffType.MISSING_IN_LDAP,
                snapshotTarget(target),
                snapshotExisting(existing),
                true
            ));
        }
        return diffs;
    }

    private void validateDuplicates(List<FeishuUserPayload> users) {
        Map<String, Integer> externalIdCounter = new LinkedHashMap<>();
        Map<String, Integer> usernameCounter = new LinkedHashMap<>();
        Map<String, Integer> employeeNoCounter = new LinkedHashMap<>();
        for (FeishuUserPayload payload : users) {
            externalIdCounter.merge(payload.externalId(), 1, Integer::sum);
            usernameCounter.merge(payload.username(), 1, Integer::sum);
            if (payload.employeeNo() != null && !payload.employeeNo().isBlank()) {
                employeeNoCounter.merge(payload.employeeNo(), 1, Integer::sum);
            }
        }
        externalIdCounter.forEach((externalId, count) -> {
            if (count > 1) {
                throw new BizException("FEISHU_USER_DUPLICATE_EXTERNAL_ID", "飞书用户 external_id 重复");
            }
        });
        usernameCounter.forEach((username, count) -> {
            if (count > 1) {
                throw new BizException("FEISHU_USER_DUPLICATE_USERNAME", "飞书用户 username 重复");
            }
        });
        employeeNoCounter.forEach((employeeNo, count) -> {
            if (count > 1) {
                throw new BizException("FEISHU_USER_DUPLICATE_EMPLOYEE_NO", "飞书用户 employee_no 重复");
            }
        });
    }

    private UserStatus normalizeStatus(Integer status) {
        return status == null ? UserStatus.ENABLED : UserStatus.fromCode(status);
    }

    private String buildUserDn(String username) {
        return LdapDnHelper.buildUserDn(ldapProperties, username);
    }

    private String snapshotTarget(User user) {
        return """
            {"externalId":"%s","username":"%s","realName":"%s","employeeNo":"%s","deptCode":"%s","status":%s}
            """.formatted(
            safe(user.getExternalId()),
            safe(user.getUsername()),
            safe(user.getRealName()),
            safe(user.getEmployeeNo()),
            safe(user.getDeptCode()),
            user.getStatus().getCode()
        );
    }

    private String snapshotExisting(User user) {
        return """
            {"externalId":"%s","username":"%s","realName":"%s","employeeNo":"%s","deptCode":"%s","status":%s,"ldapDn":"%s"}
            """.formatted(
            safe(user.getExternalId()),
            safe(user.getUsername()),
            safe(user.getRealName()),
            safe(user.getEmployeeNo()),
            safe(user.getDeptCode()),
            user.getStatus().getCode(),
            safe(user.getLdapDn())
        );
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
        Department department,
        ChangeType changeType,
        boolean ldapRepairRequired
    ) {
    }

    private enum ChangeType {
        NEW,
        UPDATE,
        NO_CHANGE
    }
}
