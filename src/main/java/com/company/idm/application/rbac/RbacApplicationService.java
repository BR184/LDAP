package com.company.idm.application.rbac;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.rbac.Permission;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色权限应用服务，负责角色维护与授权编排。
 */
@Service
@RequiredArgsConstructor
public class RbacApplicationService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditLogRepository auditLogRepository;
    private final PolicyRefreshService policyRefreshService;

    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    public List<Permission> listPermissions() {
        return permissionRepository.findAll();
    }

    @Transactional
    public Role createRole(CreateRoleCommand command, String operator) {
        roleRepository.findByCode(command.roleCode())
            .ifPresent(role -> {
                throw new BizException("ROLE_CODE_DUPLICATE", "角色编码已存在");
            });
        Role role = roleRepository.save(Role.builder()
            .roleCode(command.roleCode())
            .roleName(command.roleName())
            .remark(command.remark())
            .status(1)
            .build());
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType("ROLE_CREATE")
            .bizType("ROLE")
            .bizId(String.valueOf(role.getId()))
            .afterJson(role.getRoleCode())
            .result("SUCCESS")
            .build());
        return role;
    }

    @Transactional
    public void grantPermissions(GrantRolePermissionsCommand command) {
        roleRepository.findById(command.roleId())
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "角色不存在"));
        roleRepository.assignPermissions(command.roleId(), command.permissionIds());
        policyRefreshService.refresh();
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("ROLE_PERMISSION_GRANT")
            .bizType("ROLE")
            .bizId(String.valueOf(command.roleId()))
            .afterJson(String.valueOf(command.permissionIds()))
            .result("SUCCESS")
            .build());
    }
}

