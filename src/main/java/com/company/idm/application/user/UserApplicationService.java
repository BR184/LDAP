package com.company.idm.application.user;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.user.PasswordPolicyValidator;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户应用服务，负责用户创建、状态变更与 LDAP 协调。
 */
@Service
@RequiredArgsConstructor
public class UserApplicationService {

    private static final String DEFAULT_RESET_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LdapDirectoryService ldapDirectoryService;
    private final AuditLogRepository auditLogRepository;
    private final PolicyRefreshService policyRefreshService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PermissionLevelRuleService permissionLevelRuleService;

    /**
     * 查询当前所有未逻辑删除的用户，用于后台用户列表展示。
     */
    public List<User> listUsers() {
        return userRepository.findAll();
    }

    /**
     * 创建用户并同步写入 LDAP。
     * 先完成基础校验和数据库落库，再补写 LDAP DN，最后建立用户与角色关系并刷新权限策略。
     */
    @Transactional
    public User createUser(CreateUserCommand command) {
        passwordPolicyValidator.validate(command.initialPassword());
        userRepository.findByUsername(command.username())
            .ifPresent(user -> {
                throw new BizException("USER_DUPLICATE", "用户名已存在");
            });
        if (command.deptCode() != null && !command.deptCode().isBlank()) {
            departmentRepository.findByDeptCode(command.deptCode())
                .orElseThrow(() -> new BizException("DEPT_NOT_FOUND", "部门不存在"));
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
        String ldapDn = ldapDirectoryService.createUser(saved, command.initialPassword());
        saved = userRepository.save(saved.toBuilder().ldapDn(ldapDn).build());
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

    /**
     * 更新用户基础资料。
     * 用户名、来源等身份主键属性不在此方法中修改，仅维护展示信息与组织归属。
     */
    @Transactional
    public User updateUser(UpdateUserCommand command) {
        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        permissionLevelRuleService.checkCanModifyBasicUser(command.operator(), user);
        if (command.deptCode() != null && !command.deptCode().isBlank()) {
            departmentRepository.findByDeptCode(command.deptCode())
                .orElseThrow(() -> new BizException("DEPT_NOT_FOUND", "部门不存在"));
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

    /**
     * 更新用户启停状态，并同步使历史 token 失效。
     * 当用户状态切换时，同时驱动 LDAP 账户启停，保证平台认证与目录认证口径一致。
     */
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

    /**
     * 删除用户。
     * 按当前业务规则，先在 LDAP 中物理删除，再在 MySQL 中逻辑删除，便于再次入职时恢复业务数据。
     */
    @Transactional
    public void deleteUser(DeleteUserCommand command) {
        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        permissionLevelRuleService.checkCanModifySensitiveUser(command.operator(), user);
        // 目录侧先删条目，避免逻辑删除后仍可通过 LDAP 认证。
        ldapDirectoryService.deleteUser(user.getUsername());
        // 业务库只做逻辑删除，保留审计和后续恢复基础。
        userRepository.logicalDelete(command.userId(), nextTokenVersion(user));
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("USER_DELETE")
            .bizType("USER")
            .bizId(String.valueOf(command.userId()))
            .afterJson("DELETED")
            .result("SUCCESS")
            .build());
    }

    /**
     * 用户本人修改密码。
     * 通过 LDAP 校验旧密码正确性，改密成功后递增 tokenVersion，使旧登录态立即失效。
     */
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

    /**
     * 管理员重置目标用户密码。
     * 当前阶段按固定密码 123456 重置，且不启用首次登录强制改密逻辑。
     */
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

    /**
     * 统一计算用户下一次 tokenVersion。
     * 用于禁用、删除、改密、重置密码等需要立即踢出旧会话的场景。
     */
    private int nextTokenVersion(User user) {
        return user.getTokenVersion() == null ? 1 : user.getTokenVersion() + 1;
    }
}
