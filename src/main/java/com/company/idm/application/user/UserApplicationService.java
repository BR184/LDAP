package com.company.idm.application.user;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
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

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LdapDirectoryService ldapDirectoryService;
    private final AuditLogRepository auditLogRepository;
    private final PolicyRefreshService policyRefreshService;

    public List<User> listUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User createUser(CreateUserCommand command) {
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

    @Transactional
    public void updateStatus(UpdateUserStatusCommand command) {
        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        int nextTokenVersion = user.getTokenVersion() == null ? 1 : user.getTokenVersion() + 1;
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
}
