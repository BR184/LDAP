package com.company.idm.application.user;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.user.PasswordPolicyValidator;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.AppLdapProperties;
import com.company.idm.infrastructure.ldap.LdapDnHelper;
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

    private static final String DEFAULT_INITIAL_PASSWORD = "123456";
    private static final String DEFAULT_RESET_PASSWORD = "123456";
    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";
    private static final String ADMIN_ROLE_CODE = "ADMIN";

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LdapDirectoryService ldapDirectoryService;
    private final LdapGroupService ldapGroupService;
    private final AuditLogRepository auditLogRepository;
    private final PolicyRefreshService policyRefreshService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PermissionLevelRuleService permissionLevelRuleService;
    private final AppLdapProperties ldapProperties;

    public List<User> listUsers(String username, String departmentKeyword, Integer statusCode) {
        String normalizedUsername = normalize(username);
        String normalizedDepartmentKeyword = normalize(departmentKeyword);
        Map<String, Department> departmentByCode = loadDepartmentMap();
        return userRepository.findByConditions(normalizedUsername, null, statusCode).stream()
            .map(user -> enrichDepartment(user, departmentByCode))
            .filter(user -> matchesDepartmentKeyword(user, normalizedDepartmentKeyword))
            .sorted((left, right) -> compareUsersForList(left, right, departmentByCode))
            .toList();
    }

    public User getUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        return enrichDepartment(user, loadDepartmentMap());
    }

    @Transactional
    public User createUser(CreateUserCommand command) {
        passwordPolicyValidator.validate(DEFAULT_INITIAL_PASSWORD);
        Department department = null;
        userRepository.findByUsername(command.username())
            .ifPresent(user -> {
                throw new BizException("USER_DUPLICATE", "用户名已存在");
            });
        if (command.deptCode() != null && !command.deptCode().isBlank()) {
            department = loadEnabledDepartment(command.deptCode());
        }
        if (ldapDirectoryService.existsByUid(command.username())) {
            throw new BizException("LDAP_UID_DUPLICATE", "LDAP 用户已存在");
        }
        User saved = userRepository.save(User.builder()
            .username(command.username())
            .realName(command.realName())
            .email(command.email())
            .mobile(command.mobile())
            .employeeNo(command.employeeNo())
            .deptCode(command.deptCode())
            .status(UserStatus.ENABLED)
            .sourceType(SourceType.MANUAL)
            .tokenVersion(0)
            .build());
        String ldapDn = ldapDirectoryService.createUser(saved, DEFAULT_INITIAL_PASSWORD);
        saved = userRepository.save(saved.toBuilder().ldapDn(ldapDn).build());
        syncUserDepartmentGroup(saved, department);
        userRepository.assignRoles(saved.getId(), command.roleIds());
        policyRefreshService.refresh();
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("USER_CREATE")
            .bizType("USER")
            .bizId(String.valueOf(saved.getId()))
            .afterJson(saved.getUsername())
            .result("SUCCESS")
            .build());
        return saved;
    }

    @Transactional
    public User updateUser(UpdateUserCommand command) {
        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        permissionLevelRuleService.checkCanModifyBasicUser(command.operator(), user);
        Department department = null;
        if (command.deptCode() != null && !command.deptCode().isBlank()) {
            department = loadEnabledDepartment(command.deptCode());
        }
        User updated = user.toBuilder()
            .realName(command.realName())
            .email(command.email())
            .mobile(command.mobile())
            .employeeNo(command.employeeNo())
            .deptCode(command.deptCode())
            .build();
        userRepository.updateProfile(updated);
        ldapDirectoryService.updateUser(updated);
        syncUserDepartmentMembership(user, updated, department);
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("USER_UPDATE")
            .bizType("USER")
            .bizId(String.valueOf(command.userId()))
            .afterJson(updated.getUsername())
            .result("SUCCESS")
            .build());
        return updated;
    }

    @Transactional
    public void updateStatus(UpdateUserStatusCommand command) {
        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        permissionLevelRuleService.checkCanModifySensitiveUser(command.operator(), user);
        int nextTokenVersion = nextTokenVersion(user);
        userRepository.updateStatus(command.userId(), command.statusCode(), nextTokenVersion);
        if (command.statusCode() == UserStatus.ENABLED.getCode()) {
            ldapDirectoryService.enableUser(user.getUsername());
        } else {
            ldapDirectoryService.disableUser(user.getUsername());
        }
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("USER_STATUS_CHANGE")
            .bizType("USER")
            .bizId(String.valueOf(command.userId()))
            .afterJson(String.valueOf(command.statusCode()))
            .result("SUCCESS")
            .build());
    }

    @Transactional
    public void deleteUser(DeleteUserCommand command) {
        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        permissionLevelRuleService.checkCanModifySensitiveUser(command.operator(), user);
        deleteUserInternal(user, command.operator());
    }

    @Transactional
    public BatchDeleteUsersResult batchDeleteUsers(BatchDeleteUsersCommand command) {
        List<Long> uniqueUserIds = command.userIds().stream()
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                List::copyOf
            ));
        if (uniqueUserIds.isEmpty()) {
            throw new BizException("USER_BATCH_DELETE_EMPTY", "待删除用户不能为空");
        }

        List<User> users = uniqueUserIds.stream()
            .map(userId -> userRepository.findById(userId)
                .orElseThrow(() -> new BizException("USER_NOT_FOUND", "存在待删除用户不存在")))
            .toList();

        for (User user : users) {
            if (command.operator().equals(user.getUsername())) {
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
            .bizId(uniqueUserIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")))
            .afterJson("""
                {"totalCount":%s,"deletedCount":%s}
                """.formatted(uniqueUserIds.size(), users.size()))
            .result("SUCCESS")
            .build());
        return new BatchDeleteUsersResult(uniqueUserIds.size(), users.size());
    }

    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        User user = userRepository.findByUsername(command.operator())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        if (!ldapDirectoryService.authenticate(command.operator(), command.oldPassword())) {
            throw new BizException("OLD_PASSWORD_INVALID", "旧密码错误");
        }
        if (!command.newPassword().equals(command.confirmPassword())) {
            throw new BizException("PASSWORD_CONFIRM_MISMATCH", "两次输入的新密码不一致");
        }
        if (command.oldPassword().equals(command.newPassword())) {
            throw new BizException("PASSWORD_SAME_AS_OLD", "新密码不能与旧密码相同");
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

    @Transactional
    public String resetPassword(ResetPasswordCommand command) {
        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        permissionLevelRuleService.checkCanModifySensitiveUser(command.operator(), user);
        passwordPolicyValidator.validate(DEFAULT_RESET_PASSWORD);
        ldapDirectoryService.resetPassword(user.getUsername(), DEFAULT_RESET_PASSWORD);
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
        Department department = null;
        if (user.getDeptCode() != null && !user.getDeptCode().isBlank()) {
            department = loadEnabledDepartment(user.getDeptCode());
        }
        String ldapDn;
        if (ldapDirectoryService.existsByUid(user.getUsername())) {
            ldapDirectoryService.updateUser(user);
            ldapDn = user.getLdapDn() != null && !user.getLdapDn().isBlank()
                ? user.getLdapDn()
                : buildUserDn(user.getUsername());
        } else {
            ldapDn = ldapDirectoryService.createUser(user, DEFAULT_RESET_PASSWORD);
        }
        if (user.getStatus() == UserStatus.ENABLED) {
            ldapDirectoryService.enableUser(user.getUsername());
        } else {
            ldapDirectoryService.disableUser(user.getUsername());
        }
        if (department != null) {
            String groupDn = ldapGroupService.createGroup(department.getDeptCode(), department.getDeptName());
            updateDepartmentLdapDn(department, groupDn);
            ldapGroupService.syncUserGroups(user.getUsername(), List.of(department.getDeptCode()));
        } else {
            ldapGroupService.removeUserFromAllGroups(user.getUsername());
        }
        User synced = user;
        if (!ldapDn.equals(user.getLdapDn())) {
            synced = userRepository.save(user.toBuilder().ldapDn(ldapDn).build());
        }
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType("USER_SYNC_LDAP")
            .bizType("USER")
            .bizId(String.valueOf(userId))
            .afterJson(synced.getUsername())
            .result("SUCCESS")
            .build());
        return synced;
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
        return user.toBuilder()
            .deptName(department == null ? null : department.getDeptName())
            .build();
    }

    private boolean matchesDepartmentKeyword(User user, String departmentKeyword) {
        if (departmentKeyword == null || departmentKeyword.isBlank()) {
            return true;
        }
        String keyword = departmentKeyword.toLowerCase(Locale.ROOT);
        boolean deptNameMatches = user.getDeptName() != null
            && user.getDeptName().toLowerCase(Locale.ROOT).contains(keyword);
        boolean deptCodeMatches = user.getDeptCode() != null
            && user.getDeptCode().equalsIgnoreCase(departmentKeyword);
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
        int byStatus = compareNullable(
            left.getStatus() == null ? null : left.getStatus().getCode(),
            right.getStatus() == null ? null : right.getStatus().getCode()
        );
        if (byStatus != 0) {
            return byStatus;
        }
        int byRealName = compareNullable(safe(left.getRealName()), safe(right.getRealName()));
        if (byRealName != 0) {
            return byRealName;
        }
        return compareNullable(safe(left.getUsername()), safe(right.getUsername()));
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
        return compareNullable(safe(left.getUsername()), safe(right.getUsername()));
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

    private void syncUserDepartmentGroup(User user, Department department) {
        if (department == null) {
            return;
        }
        String ldapDn = ldapGroupService.createGroup(department.getDeptCode(), department.getDeptName());
        updateDepartmentLdapDn(department, ldapDn);
        ldapGroupService.addUserToGroup(user.getUsername(), department.getDeptCode());
    }

    private void syncUserDepartmentMembership(User originalUser, User updatedUser, Department newDepartment) {
        String oldDeptCode = originalUser.getDeptCode();
        String newDeptCode = updatedUser.getDeptCode();
        if (oldDeptCode != null && !oldDeptCode.isBlank() && !oldDeptCode.equals(newDeptCode)) {
            ldapGroupService.removeUserFromGroup(updatedUser.getUsername(), oldDeptCode);
        }
        if (newDepartment != null) {
            String ldapDn = ldapGroupService.createGroup(newDepartment.getDeptCode(), newDepartment.getDeptName());
            updateDepartmentLdapDn(newDepartment, ldapDn);
        }
        if (newDeptCode != null && !newDeptCode.isBlank()) {
            ldapGroupService.addUserToGroup(updatedUser.getUsername(), newDeptCode);
        } else if (oldDeptCode != null && !oldDeptCode.isBlank()) {
            ldapGroupService.removeUserFromAllGroups(updatedUser.getUsername());
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

    private Department loadEnabledDepartment(String deptCode) {
        Department department = departmentRepository.findByDeptCode(deptCode)
            .orElseThrow(() -> new BizException("DEPT_NOT_FOUND", "部门不存在"));
        if (department.getStatus() == null || department.getStatus() != 1) {
            throw new BizException("DEPT_DISABLED", "部门已停用");
        }
        return department;
    }

    private String buildUserDn(String username) {
        return LdapDnHelper.buildUserDn(ldapProperties, username);
    }

    private String buildRecycledUsername(User user) {
        return user.getUsername() + "__deleted__" + user.getId();
    }

    private void deleteUserInternal(User user, String operator) {
        ldapGroupService.removeUserFromAllGroups(user.getUsername());
        ldapDirectoryService.deleteUser(user.getUsername());
        userRepository.removeAllRoles(user.getId());
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

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
