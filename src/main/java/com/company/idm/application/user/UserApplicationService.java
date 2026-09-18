package com.company.idm.application.user;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.common.api.PageResult;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rolegroup.RoleSupplyEventDraft;
import com.company.idm.domain.rolegroup.RoleSupplyEventRecorder;
import com.company.idm.domain.rolegroup.RoleSupplyEventType;
import com.company.idm.domain.user.PasswordPolicyValidator;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserAccessPolicy;
import com.company.idm.domain.user.UserPageQuery;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.AppLdapProperties;
import com.company.idm.infrastructure.ldap.LdapDnHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserApplicationService {

    private static final String DEFAULT_RESET_PASSWORD = "123456";
    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";
    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final int BATCH_DELETE_AUDIT_SAMPLE_SIZE = 10;

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LdapDirectoryService ldapDirectoryService;
    private final LdapGroupService ldapGroupService;
    private final AuditLogRepository auditLogRepository;
    private final PolicyRefreshService policyRefreshService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PermissionLevelRuleService permissionLevelRuleService;
    private final RoleRepository roleRepository;
    private final IntranetEmailGenerationService intranetEmailGenerationService;
    private final AppLdapProperties ldapProperties;
    private final PasswordVerificationTokenService passwordVerificationTokenService;
    private final InitialPasswordPolicy initialPasswordPolicy;
    private final UserAccessPolicy userAccessPolicy;
    private final SystemAdministratorProtectionPolicy systemAdministratorProtectionPolicy;
    private final UserReadScopeService userReadScopeService;
    private final RoleSupplyEventRecorder roleSupplyEventRecorder;

    /**
     * V1 keyword 模糊搜索：跨 userId/realName/employeeNo 三字段模糊匹配，管理端搜索框与批量删除共用。
     * 仅供 V1 兼容，新功能请使用 V2 的 GET /api/v2/users。
     */
    public List<User> listUsersByKeyword(String keyword, String departmentKeyword, Boolean accessAllowed) {
        String normalizedKeyword = normalize(keyword);
        String normalizedDepartmentKeyword = normalize(departmentKeyword);
        Map<String, Department> departmentByCode = loadDepartmentMap();
        return userRepository.findByConditions(normalizedKeyword, null, accessAllowed).stream()
            .map(user -> enrichDepartment(user, departmentByCode))
            .filter(user -> matchesDepartmentKeyword(user, normalizedDepartmentKeyword))
            .sorted((left, right) -> compareUsersForList(left, right, departmentByCode))
            .toList();
    }

    /**
     * V1 userId 精确匹配：只返回与 userId 精确相等的用户（0 或 1 条），供第三方身份目录精确查询，
     * 避免子串模糊命中产生歧义。仅供 V1 兼容，新功能请使用 V2 的 GET /api/v2/users/by-user-id/{userId}。
     */
    public List<User> listUsersByExactUserId(String userId, String departmentKeyword, Boolean accessAllowed) {
        String normalizedUserId = normalize(userId);
        if (normalizedUserId == null) {
            return List.of();
        }
        String normalizedDepartmentKeyword = normalize(departmentKeyword);
        Map<String, Department> departmentByCode = loadDepartmentMap();
        return userRepository.findByUserId(normalizedUserId)
            .map(user -> enrichDepartment(user, departmentByCode))
            .filter(user -> matchesDepartmentKeyword(user, normalizedDepartmentKeyword))
            .filter(user -> accessAllowed == null || user.isAccessAllowed() == accessAllowed)
            .stream()
            .toList();
    }

    /**
     * V2 用户分页查询：读取范围下推到 SQL 后再分页，保证 total 正确。
     *
     * @param spec                       V2 查询规格（筛选 + 部门规则 + 分页 + 排序）
     * @param operator                   当前操作者
     * @param organizationSnapshot       组织快照（用于下属树范围计算）
     * @param operatorPermissionCodes    操作者权限码
     */
    public PageResult<User> pageUsersV2(
        UserPageQuery spec,
        User operator,
        List<User> organizationSnapshot,
        Set<String> operatorPermissionCodes
    ) {
        Set<Long> visibleUserIds = userReadScopeService.resolveVisibleUserIds(operator, organizationSnapshot, operatorPermissionCodes);
        if (visibleUserIds != null && visibleUserIds.isEmpty()) {
            return PageResult.empty(spec.pageNum(), spec.pageSize());
        }
        Map<String, Department> departmentByCode = loadDepartmentMap();
        PageResult<User> page = userRepository.pageFind(spec, visibleUserIds);
        List<User> enriched = page.items().stream()
            .map(user -> enrichDepartment(user, departmentByCode))
            .toList();
        return PageResult.of(enriched, page.total(), page.pageNum(), page.pageSize());
    }

    public User getUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        return enrichDepartment(user, loadDepartmentMap());
    }

    /**
     * 按业务用户ID精确查询单个用户（V2 by-user-id 端点，走唯一索引）。
     */
    public User getUserByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BizException("USER_NOT_FOUND", "用户不存在");
        }
        User user = userRepository.findByUserId(userId.trim())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        return enrichDepartment(user, loadDepartmentMap());
    }

    @Transactional
    public User createUser(CreateUserCommand command) {
        DepartmentAssignment departmentAssignment = resolveDepartmentAssignment(command.deptCode(), command.partTimeDeptCodes());
        List<Role> roles = loadEnabledRoles(command.roleIds());
        String normalizedUserId = requireUserIdentifier(command.userId());
        systemAdministratorProtectionPolicy.checkUserIdAvailableForCreation(normalizedUserId);
        String normalizedIntranetEmail = normalizeRequiredIntranetEmail(command.intranetEmail(), null);

        userRepository.findByEmployeeNo(command.employeeNo())
            .ifPresent(user -> {
                throw new BizException("USER_EMPLOYEE_NO_DUPLICATE", "工号已存在");
            });
        userRepository.findByUserId(normalizedUserId)
            .ifPresent(user -> {
                throw new BizException("USER_IDENTIFIER_DUPLICATE", "用户ID已存在");
            });
        if (ldapDirectoryService.existsByUid(normalizedUserId)) {
            throw new BizException("LDAP_UID_DUPLICATE", "LDAP 用户已存在");
        }

        User saved = userRepository.save(User.builder()
            .userId(normalizedUserId)
            .realName(command.realName())
            .email(command.email())
            .intranetEmail(normalizedIntranetEmail)
            .mobile(command.mobile())
            .employeeNo(command.employeeNo())
            .deptCode(departmentAssignment.mainDepartmentCode())
            .partTimeDeptCodes(departmentAssignment.partTimeDeptCodes())
            .accessAllowed(command.accessAllowed())
            .employmentStatus(EmploymentStatus.ACTIVE)
            .accountStatus("正常")
            .sourceType(SourceType.MANUAL)
            .tokenVersion(0)
            .build());
        permissionLevelRuleService.checkCanAssignRoles(command.operator(), saved, roles);
        String initialPassword = initialPasswordPolicy.resolve(saved.getMobile());
        passwordPolicyValidator.validate(initialPassword);
        String ldapDn = ldapDirectoryService.createUser(saved, initialPassword);
        saved = userRepository.save(saved.toBuilder().ldapDn(ldapDn).build());
        syncLdapAccessState(saved);
        syncUserDepartmentGroups(saved);
        userRepository.assignRoles(saved.getId(), command.roleIds(), command.operator());
        saved = saved.toBuilder()
            .roleCodes(roles.stream().map(Role::getRoleCode).collect(java.util.stream.Collectors.toSet()))
            .permissionLevel(resolvePermissionLevel(roles))
            .build();
        policyRefreshService.refresh();
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("USER_CREATE")
            .bizType("USER")
            .bizId(String.valueOf(saved.getId()))
            .afterJson(saved.getUserId())
            .result("SUCCESS")
            .build());
        return enrichDepartment(saved, loadDepartmentMap());
    }

    @Transactional
    public User updateUser(UpdateUserCommand command) {
        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        systemAdministratorProtectionPolicy.checkProfileMutable(user);
        permissionLevelRuleService.checkCanModifyBasicUser(command.operator(), user);
        validateEditableEmployeeNo(command.employeeNo(), user);
        validateImmutableUserIdentifier(command.userIdentifier(), user);
        DepartmentAssignment departmentAssignment = resolveDepartmentAssignment(command.deptCode(), command.partTimeDeptCodes());

        User updated = user.toBuilder()
            .realName(command.realName())
            .email(command.email())
            .intranetEmail(resolveUpdatedIntranetEmail(command.intranetEmail(), user))
            .mobile(command.mobile())
            .employeeNo(command.employeeNo())
            .deptCode(departmentAssignment.mainDepartmentCode())
            .partTimeDeptCodes(departmentAssignment.partTimeDeptCodes())
            .build();
        userRepository.updateProfile(updated);
        ldapDirectoryService.updateUser(updated);
        syncUserDepartmentGroups(updated);
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("USER_UPDATE")
            .bizType("USER")
            .bizId(String.valueOf(command.userId()))
            .afterJson(updated.getUserId())
            .result("SUCCESS")
            .build());
        return enrichDepartment(updated, loadDepartmentMap());
    }

    @Transactional
    public void updateAccess(UpdateUserAccessCommand command) {
        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        systemAdministratorProtectionPolicy.checkAccessChangeAllowed(user, command.accessAllowed());
        permissionLevelRuleService.checkCanModifySensitiveUser(command.operator(), user);
        int nextTokenVersion = nextTokenVersion(user);
        User updated = user.toBuilder()
            .accessAllowed(command.accessAllowed())
            .tokenVersion(nextTokenVersion)
            .build();
        userRepository.updateAccessAllowed(command.userId(), command.accessAllowed(), nextTokenVersion, command.operator());
        syncLdapAccessState(updated);
        recordAccessChangeEvents(user, command.accessAllowed(), command.operator());
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("USER_ACCESS_CHANGE")
            .bizType("USER")
            .bizId(String.valueOf(command.userId()))
            .beforeJson(String.valueOf(user.isAccessAllowed()))
            .afterJson(String.valueOf(command.accessAllowed()))
            .result("SUCCESS")
            .build());
    }

    @Transactional
    public void deleteUser(DeleteUserCommand command) {
        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        systemAdministratorProtectionPolicy.checkDeletable(user);
        ensureDeletableSource(user);
        ensureNotSuperAdmin(user);
        permissionLevelRuleService.checkCanModifySensitiveUser(command.operator(), user);
        deleteUserInternal(user, command.operator());
    }

    @Transactional
    public BatchDeleteUsersResult batchDeleteUsers(BatchDeleteUsersCommand command) {
        List<Long> uniqueUserIds = command.userIds() == null ? List.of() : command.userIds().stream()
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                List::copyOf
            ));

        List<User> users;
        if (!uniqueUserIds.isEmpty()) {
            users = uniqueUserIds.stream()
                .map(userId -> userRepository.findById(userId)
                    .orElseThrow(() -> new BizException("USER_NOT_FOUND", "存在待删除用户不存在")))
                .toList();
        } else {
            users = listUsersByKeyword(command.userIdKeyword(), command.deptNameKeyword(), command.accessAllowed());
        }
        if (users.isEmpty()) {
            throw new BizException("USER_BATCH_DELETE_EMPTY", "待删除用户不能为空");
        }

        for (User user : users) {
            systemAdministratorProtectionPolicy.checkDeletable(user);
            ensureDeletableSource(user);
            ensureNotSuperAdmin(user);
            if (command.operator().equals(user.getUserId())) {
                throw new BizException("USER_BATCH_DELETE_SELF_FORBIDDEN", "不允许批量删除当前登录用户");
            }
            permissionLevelRuleService.checkCanModifySensitiveUser(command.operator(), user);
        }

        for (User user : users) {
            deleteUserInternal(user, command.operator());
        }

        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("USER_BATCH_DELETE")
            .bizType("USER")
            .bizId("USER_BATCH_DELETE")
            .afterJson(buildBatchDeleteAuditSummary(users))
            .result("SUCCESS")
            .build());
        return new BatchDeleteUsersResult(users.size(), users.size());
    }

    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        User user = userRepository.findByUserId(command.operator())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        passwordVerificationTokenService.verify(command.verificationToken(), user);
        if (!command.newPassword().equals(command.confirmPassword())) {
            throw new BizException("PASSWORD_CONFIRM_MISMATCH", "两次输入的新密码不一致");
        }
        passwordPolicyValidator.validate(command.newPassword());
        ldapDirectoryService.resetPassword(command.operator(), command.newPassword());
        userRepository.bumpTokenVersion(user.getId(), nextTokenVersion(user));
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("USER_PASSWORD_CHANGE")
            .bizType("USER")
            .bizId(String.valueOf(user.getId()))
            .afterJson("CHANGED")
            .result("SUCCESS")
            .build());
    }

    public String verifyPassword(String operator, String oldPassword) {
        User user = userRepository.findByUserId(operator)
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        verifyOldPassword(operator, oldPassword);
        return passwordVerificationTokenService.generate(user);
    }

    @Transactional
    public String resetPassword(ResetPasswordCommand command) {
        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        permissionLevelRuleService.checkCanModifySensitiveUser(command.operator(), user);
        passwordPolicyValidator.validate(DEFAULT_RESET_PASSWORD);
        ldapDirectoryService.resetPassword(user.getUserId(), DEFAULT_RESET_PASSWORD);
        userRepository.bumpTokenVersion(user.getId(), nextTokenVersion(user));
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("USER_PASSWORD_RESET")
            .bizType("USER")
            .bizId(String.valueOf(user.getId()))
            .afterJson("RESET")
            .result("SUCCESS")
            .build());
        return DEFAULT_RESET_PASSWORD;
    }

    @Transactional
    public User syncUserToLdap(Long userId, String operator) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        permissionLevelRuleService.checkCanModifySensitiveUser(operator, user);
        resolveDepartmentAssignment(user.getDeptCode(), user.getPartTimeDeptCodes());
        String ldapDn;
        if (ldapDirectoryService.existsByUid(user.getUserId())) {
            ldapDirectoryService.updateUser(user);
            ldapDn = user.getLdapDn() != null && !user.getLdapDn().isBlank()
                ? user.getLdapDn()
                : buildUserDn(user.getUserId());
        } else {
            String initialPassword = initialPasswordPolicy.resolve(user.getMobile());
            passwordPolicyValidator.validate(initialPassword);
            ldapDn = ldapDirectoryService.createUser(user, initialPassword);
        }
        syncLdapAccessState(user);
        syncUserDepartmentGroups(user);
        User synced = user;
        if (!ldapDn.equals(user.getLdapDn())) {
            synced = userRepository.save(user.toBuilder().ldapDn(ldapDn).build());
        }
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType("USER_SYNC_LDAP")
            .bizType("USER")
            .bizId(String.valueOf(userId))
            .afterJson(synced.getUserId())
            .result("SUCCESS")
            .build());
        return enrichDepartment(synced, loadDepartmentMap());
    }

    private int nextTokenVersion(User user) {
        return user.getTokenVersion() == null ? 1 : user.getTokenVersion() + 1;
    }

    private Map<String, Department> loadDepartmentMap() {
        return departmentRepository.findAll().stream()
            .filter(department -> department.getDeptCode() != null && !department.getDeptCode().isBlank())
            .collect(
                LinkedHashMap::new,
                (accumulator, department) -> accumulator.putIfAbsent(department.getDeptCode(), department),
                LinkedHashMap::putAll
            );
    }

    private User enrichDepartment(User user, Map<String, Department> departmentByCode) {
        if (user == null) {
            return null;
        }
        Department department = user.getDeptCode() == null ? null : departmentByCode.get(user.getDeptCode());
        List<String> partTimeDeptCodes = normalizePartTimeDeptCodes(user.getDeptCode(), user.getPartTimeDeptCodes());
        return user.toBuilder()
            .deptName(department == null ? null : department.getDeptName())
            .departmentPath(resolveDepartmentPath(department, departmentByCode))
            .partTimeDeptCodes(partTimeDeptCodes)
            .permissionLevel(resolvePermissionLevel(user.getRoleCodes()))
            .build();
    }

    private boolean matchesDepartmentKeyword(User user, String departmentKeyword) {
        if (departmentKeyword == null || departmentKeyword.isBlank()) {
            return true;
        }
        String keyword = departmentKeyword.toLowerCase(Locale.ROOT);
        boolean deptNameMatches = user.getDeptName() != null && user.getDeptName().toLowerCase(Locale.ROOT).contains(keyword);
        boolean deptCodeMatches = user.getDeptCode() != null && user.getDeptCode().equalsIgnoreCase(departmentKeyword);
        return deptNameMatches || deptCodeMatches;
    }

    private int compareUsersForList(User left, User right, Map<String, Department> departmentByCode) {
        int leftAdminRank = adminDisplayRank(left);
        int rightAdminRank = adminDisplayRank(right);
        if (leftAdminRank != rightAdminRank) {
            return Integer.compare(leftAdminRank, rightAdminRank);
        }
        if (leftAdminRank < 2) {
            return compareAdminUsers(left, right);
        }
        return compareDepartmentUsers(left, right, departmentByCode);
    }

    private int compareAdminUsers(User left, User right) {
        int byAccess = compareNullable(
            left.isAccessAllowed() ? 0 : 1,
            right.isAccessAllowed() ? 0 : 1
        );
        if (byAccess != 0) {
            return byAccess;
        }
        int byRealName = compareNullable(safe(left.getRealName()), safe(right.getRealName()));
        if (byRealName != 0) {
            return byRealName;
        }
        return compareNullable(safe(left.getUserId()), safe(right.getUserId()));
    }

    private int compareDepartmentUsers(User left, User right, Map<String, Department> departmentByCode) {
        int byDepartmentPresence = Integer.compare(
            isDepartmentMissing(left, departmentByCode) ? 1 : 0,
            isDepartmentMissing(right, departmentByCode) ? 1 : 0
        );
        if (byDepartmentPresence != 0) {
            return byDepartmentPresence;
        }
        int byAncestorPath = compareNullable(resolveAncestorPath(left, departmentByCode), resolveAncestorPath(right, departmentByCode));
        if (byAncestorPath != 0) {
            return byAncestorPath;
        }
        int byDeptName = compareNullable(safe(left.getDeptName()), safe(right.getDeptName()));
        if (byDeptName != 0) {
            return byDeptName;
        }
        int byEmployeeNo = compareNullable(safe(left.getEmployeeNo()), safe(right.getEmployeeNo()));
        if (byEmployeeNo != 0) {
            return byEmployeeNo;
        }
        int byRealName = compareNullable(safe(left.getRealName()), safe(right.getRealName()));
        if (byRealName != 0) {
            return byRealName;
        }
        return compareNullable(safe(left.getUserId()), safe(right.getUserId()));
    }

    private int adminDisplayRank(User user) {
        if (user == null || user.getRoleCodes() == null || user.getRoleCodes().isEmpty()) {
            return 2;
        }
        if (user.getRoleCodes().contains(SUPER_ADMIN_ROLE_CODE)) {
            return 0;
        }
        if (user.getRoleCodes().contains(ADMIN_ROLE_CODE)) {
            return 1;
        }
        return 2;
    }

    private boolean isDepartmentMissing(User user, Map<String, Department> departmentByCode) {
        return user == null || user.getDeptCode() == null || !departmentByCode.containsKey(user.getDeptCode());
    }

    private String resolveAncestorPath(User user, Map<String, Department> departmentByCode) {
        if (user == null || user.getDeptCode() == null || user.getDeptCode().isBlank()) {
            return "~~~~";
        }
        Department department = departmentByCode.get(user.getDeptCode());
        if (department == null || department.getAncestorPath() == null || department.getAncestorPath().isBlank()) {
            return "~~~~/" + user.getDeptCode();
        }
        return department.getAncestorPath();
    }

    private <T extends Comparable<T>> int compareNullable(T left, T right) {
        return Comparator.nullsLast(Comparator.<T>naturalOrder()).compare(left, right);
    }

    private void syncUserDepartmentGroups(User user) {
        DepartmentAssignment assignment = resolveDepartmentAssignment(user.getDeptCode(), user.getPartTimeDeptCodes());
        List<Department> departments = assignment.departments();
        if (departments.isEmpty()) {
            ldapGroupService.removeUserFromAllGroups(user.getUserId());
            return;
        }
        for (Department department : departments) {
            String ldapDn = ldapGroupService.createGroup(department.getDeptCode(), department.getDeptName());
            updateDepartmentLdapDn(department, ldapDn);
        }
        ldapGroupService.syncUserGroups(user.getUserId(), departments.stream().map(Department::getDeptCode).toList());
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

    private Department loadEnabledDepartment(String deptCode) {
        Department department = departmentRepository.findByDeptCode(deptCode)
            .orElseThrow(() -> new BizException("DEPT_NOT_FOUND", "部门不存在"));
        if (department.getStatus() == null || department.getStatus() != 1) {
            throw new BizException("DEPT_DISABLED", "部门已停用");
        }
        return department;
    }

    private List<Role> loadEnabledRoles(List<Long> roleIds) {
        List<Role> roles = roleRepository.findByIds(roleIds);
        if (roles.size() != roleIds.size()) {
            throw new BizException("ROLE_NOT_FOUND", "部分角色不存在");
        }
        boolean containsDisabledRole = roles.stream().anyMatch(role -> role.getStatus() == null || role.getStatus() != 1);
        if (containsDisabledRole) {
            throw new BizException("ROLE_ASSIGN_DISABLED", "已禁用角色不允许分配");
        }
        return roles;
    }

    private void validateEditableEmployeeNo(String employeeNo, User currentUser) {
        if (employeeNo == null || employeeNo.isBlank()) {
            throw new BizException("USER_EMPLOYEE_NO_REQUIRED", "工号不能为空");
        }
        String normalizedEmployeeNo = employeeNo.trim();
        if (currentUser != null && normalizedEmployeeNo.equals(currentUser.getEmployeeNo())) {
            return;
        }
        userRepository.findByEmployeeNo(normalizedEmployeeNo)
            .ifPresent(user -> {
                if (currentUser == null || !user.getId().equals(currentUser.getId())) {
                    throw new BizException("USER_EMPLOYEE_NO_DUPLICATE", "工号已存在");
                }
            });
    }

    private DepartmentAssignment resolveDepartmentAssignment(String mainDeptCode, List<String> rawPartTimeDeptCodes) {
        String normalizedMainDeptCode = normalize(mainDeptCode);
        List<String> normalizedPartTimeDeptCodes = normalizePartTimeDeptCodes(normalizedMainDeptCode, rawPartTimeDeptCodes);
        List<Department> departments = new ArrayList<>();
        if (normalizedMainDeptCode != null) {
            departments.add(loadEnabledDepartment(normalizedMainDeptCode));
        }
        for (String deptCode : normalizedPartTimeDeptCodes) {
            departments.add(loadEnabledDepartment(deptCode));
        }
        return new DepartmentAssignment(normalizedMainDeptCode, normalizedPartTimeDeptCodes, List.copyOf(departments));
    }

    private List<String> normalizePartTimeDeptCodes(String mainDeptCode, List<String> rawPartTimeDeptCodes) {
        if (rawPartTimeDeptCodes == null || rawPartTimeDeptCodes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalizedCodes = new LinkedHashSet<>();
        for (String deptCode : rawPartTimeDeptCodes) {
            String normalizedDeptCode = normalize(deptCode);
            if (normalizedDeptCode == null || normalizedDeptCode.equals(mainDeptCode)) {
                continue;
            }
            normalizedCodes.add(normalizedDeptCode);
        }
        return List.copyOf(normalizedCodes);
    }

    private String resolveDepartmentPath(Department department, Map<String, Department> departmentByCode) {
        if (department == null) {
            return null;
        }
        List<String> departmentNames = resolveDepartmentPathNames(department, departmentByCode);
        if (departmentNames.isEmpty()) {
            return department.getDeptName();
        }
        return String.join(" / ", departmentNames);
    }

    private List<String> resolveDepartmentPathNames(Department department, Map<String, Department> departmentByCode) {
        if (department == null) {
            return List.of();
        }
        String ancestorPath = department.getAncestorPath();
        if (ancestorPath == null || ancestorPath.isBlank()) {
            return List.of(department.getDeptName());
        }
        List<String> pathCodes = java.util.Arrays.stream(ancestorPath.split("/"))
            .map(String::trim)
            .filter(item -> !item.isBlank())
            .distinct()
            .toList();
        List<String> departmentNames = new ArrayList<>();
        for (String pathCode : pathCodes) {
            Department pathDepartment = departmentByCode.get(pathCode);
            if (pathDepartment != null && pathDepartment.getDeptName() != null && !pathDepartment.getDeptName().isBlank()) {
                departmentNames.add(pathDepartment.getDeptName());
            }
        }
        if (departmentNames.isEmpty()) {
            departmentNames.add(department.getDeptName());
        }
        return List.copyOf(departmentNames);
    }

    private Integer resolvePermissionLevel(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        return resolvePermissionLevel(roleRepository.findByCodes(roleCodes));
    }

    private Integer resolvePermissionLevel(List<Role> roles) {
        return roles.stream()
            .map(Role::getPermissionLevel)
            .filter(java.util.Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(Integer.MAX_VALUE);
    }

    private String generateIntranetEmail(String uniqueIdentifier, Long currentUserId) {
        return intranetEmailGenerationService.generate(uniqueIdentifier, currentUserId);
    }

    private String resolveExistingIntranetEmail(User user) {
        if (user == null) {
            return null;
        }
        if (safe(user.getIntranetEmail()) != null) {
            return user.getIntranetEmail();
        }
        String uniqueIdentifier = safe(user.getUserId()) != null ? user.getUserId() : String.valueOf(user.getId());
        return generateIntranetEmail(uniqueIdentifier, user.getId());
    }

    private String resolveUpdatedIntranetEmail(String intranetEmail, User user) {
        String normalizedIntranetEmail = normalizeRequiredIntranetEmail(intranetEmail, user == null ? null : user.getId());
        return normalizedIntranetEmail == null ? resolveExistingIntranetEmail(user) : normalizedIntranetEmail;
    }

    private String normalizeRequiredIntranetEmail(String intranetEmail, Long currentUserId) {
        String normalizedIntranetEmail = safe(intranetEmail);
        if (normalizedIntranetEmail == null) {
            return null;
        }
        User existing = userRepository.findByIntranetEmail(normalizedIntranetEmail).orElse(null);
        if (existing != null && (currentUserId == null || !currentUserId.equals(existing.getId()))) {
            throw new BizException("USER_INTRANET_EMAIL_DUPLICATE", "内网邮箱已存在");
        }
        return normalizedIntranetEmail.toLowerCase(Locale.ROOT);
    }

    private String requireUserIdentifier(String userIdentifier) {
        String normalizedUserIdentifier = safe(userIdentifier);
        if (normalizedUserIdentifier == null) {
            throw new BizException("USER_IDENTIFIER_REQUIRED", "用户ID不能为空");
        }
        return normalizedUserIdentifier;
    }

    private void validateImmutableUserIdentifier(String userIdentifier, User currentUser) {
        String normalizedUserIdentifier = requireUserIdentifier(userIdentifier);
        if (currentUser != null && normalizedUserIdentifier.equals(currentUser.getUserId())) {
            return;
        }
        throw new BizException("USER_IDENTIFIER_IMMUTABLE", "用户ID不允许修改");
    }

    private void ensureNotSuperAdmin(User user) {
        if (user != null && user.getRoleCodes() != null && user.getRoleCodes().contains(SUPER_ADMIN_ROLE_CODE)) {
            throw new BizException("USER_DELETE_SUPER_ADMIN_FORBIDDEN", "不允许删除超级管理员");
        }
    }

    private void syncLdapAccessState(User user) {
        if (userAccessPolicy.canAuthenticate(user)) {
            ldapDirectoryService.enableUser(user.getUserId());
            return;
        }
        ldapDirectoryService.disableUser(user.getUserId());
    }

    private void ensureDeletableSource(User user) {
        if (user != null && user.getSourceType() == SourceType.FEISHU) {
            throw new BizException("USER_FILE_MANAGED_DELETE_FORBIDDEN", "文件管理用户不能删除，请关闭允许使用");
        }
    }

    private String buildUserDn(String userId) {
        return LdapDnHelper.buildUserDn(ldapProperties, userId);
    }

    private String buildRecycledUsername(User user) {
        return user.getUserId() + "__deleted__" + user.getId();
    }

    /**
     * 记录用户访问状态变化对应的成员事实。
     *
     * <p>停用使该用户全部角色的对外授权事实消失，启用则恢复；角色绑定本身不变，
     * 因此这里只补记事实事件，不触碰角色绑定数据，避免把可逆的访问控制变成不可逆的授权删除。
     */
    private void recordAccessChangeEvents(User user, boolean accessAllowed, String operator) {
        Set<String> roleCodes = user.getRoleCodes();
        if (roleCodes == null || roleCodes.isEmpty()) {
            return;
        }
        for (Role role : roleRepository.findAll()) {
            if (!roleCodes.contains(role.getRoleCode())) {
                continue;
            }
            roleSupplyEventRecorder.record(RoleSupplyEventDraft.memberChange(
                accessAllowed ? RoleSupplyEventType.MEMBER_ADDED : RoleSupplyEventType.MEMBER_REMOVED,
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getRoleScope(),
                role.getRoleGroupId(),
                user.getId(),
                user.getRealName(),
                user.getUserId(),
                accessAllowed ? "ADDED" : "REMOVED",
                Map.of("reason", accessAllowed ? "ACCESS_GRANTED" : "ACCESS_REVOKED"),
                operator
            ));
        }
    }

    private void deleteUserInternal(User user, String operator) {
        cleanupLdapUser(user.getUserId());
        userRepository.removeAllRoles(user.getId(), operator);
        userRepository.logicalDelete(user.getId(), buildRecycledUsername(user), nextTokenVersion(user));
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType("USER_DELETE")
            .bizType("USER")
            .bizId(String.valueOf(user.getId()))
            .afterJson("DELETED")
            .result("SUCCESS")
            .build());
    }

    private void cleanupLdapUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        ldapGroupService.removeUserFromAllGroups(userId);
        if (!ldapDirectoryService.existsByUid(userId)) {
            return;
        }
        try {
            ldapDirectoryService.deleteUser(userId);
        } catch (BizException exception) {
            if (!"LDAP_USER_NOT_FOUND".equals(exception.getCode())) {
                throw exception;
            }
        }
    }

    private void verifyOldPassword(String operator, String oldPassword) {
        if (!ldapDirectoryService.authenticate(operator, oldPassword)) {
            throw new BizException("OLD_PASSWORD_INVALID", "旧密码错误");
        }
    }

    private String buildBatchDeleteAuditSummary(List<User> users) {
        List<Long> sampleUserIds = users.stream()
            .map(User::getId)
            .filter(java.util.Objects::nonNull)
            .limit(BATCH_DELETE_AUDIT_SAMPLE_SIZE)
            .toList();
        return """
            {"totalCount":%s,"deletedCount":%s,"sampleUserIds":%s,"truncated":%s}
            """.formatted(
            users.size(),
            users.size(),
            sampleUserIds,
            users.size() > BATCH_DELETE_AUDIT_SAMPLE_SIZE
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record DepartmentAssignment(
        String mainDepartmentCode,
        List<String> partTimeDeptCodes,
        List<Department> departments
    ) {
    }
}
