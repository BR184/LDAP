package com.company.idm.application.rolegroup;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.rolegroup.RoleGroupRepository;
import com.company.idm.domain.rolegroup.RoleSupplyEventDraft;
import com.company.idm.domain.rolegroup.RoleSupplyEventRecorder;
import com.company.idm.domain.rolegroup.RoleSupplyEventType;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleGovernanceApplicationService {

    private final RoleRepository roleRepository;
    private final RoleGroupRepository roleGroupRepository;
    private final AuditLogRepository auditLogRepository;
    private final PolicyRefreshService policyRefreshService;
    private final RoleGroupAuthorizationService authorizationService;
    private final RoleSupplyEventRecorder roleSupplyEventRecorder;

    public List<Role> listRoles(AuthenticatedUser principal) {
        requirePlatformAdmin(principal);
        return roleRepository.findAll();
    }

    @Transactional
    public Role updateScope(Long roleId, RoleScope roleScope, Long roleGroupId, AuthenticatedUser principal) {
        requirePlatformAdmin(principal);
        if (roleScope == null) {
            throw new BizException("ROLE_SCOPE_REQUIRED", "角色作用域不能为空");
        }
        Role role = roleRepository.findByIdForUpdate(roleId)
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "角色不存在"));
        if (Integer.valueOf(1).equals(role.getBuiltIn())) {
            throw new BizException("BUILT_IN_ROLE_SCOPE_LOCKED", "内置角色的作用域不允许修改");
        }
        Long normalizedGroupId = validateScopeTarget(roleId, roleScope, roleGroupId);
        boolean scopeChanged = !Objects.equals(role.getRoleScope(), roleScope)
            || !Objects.equals(role.getRoleGroupId(), normalizedGroupId);
        if (scopeChanged
            && role.getRoleScope() == RoleScope.GROUP
            && role.getRoleGroupId() != null) {
            // 旧组范围：角色移出等价于删除，旧组订阅方据此撤销该角色授权。
            roleSupplyEventRecorder.record(RoleSupplyEventDraft.roleEvent(
                role,
                RoleSupplyEventType.ROLE_DELETED,
                Map.of("reason", "SCOPE_MOVED_OUT"),
                principal.userId()));
        }
        Role saved = roleRepository.save(Role.builder()
            .id(role.getId())
            .roleCode(role.getRoleCode())
            .roleName(role.getRoleName())
            .permissionLevel(role.getPermissionLevel())
            .builtIn(role.getBuiltIn())
            .status(role.getStatus())
            .remark(role.getRemark())
            .roleScope(roleScope)
            .roleGroupId(normalizedGroupId)
            .build());
        if (scopeChanged
            && saved.getRoleScope() == RoleScope.GROUP
            && saved.getRoleGroupId() != null) {
            // 新组范围：角色纳入，新组订阅方据此发现角色并接收后续成员变化。
            roleSupplyEventRecorder.record(RoleSupplyEventDraft.roleEvent(
                saved,
                RoleSupplyEventType.ROLE_CREATED,
                Map.of("reason", "SCOPE_MOVED_IN"),
                principal.userId()));
        }
        policyRefreshService.refresh();
        auditLogRepository.save(AuditLog.builder()
            .operator(principal.userId())
            .operationType("ROLE_SCOPE_UPDATE")
            .bizType("ROLE")
            .bizId(String.valueOf(roleId))
            .beforeJson(scopeLabel(role.getRoleScope(), role.getRoleGroupId()))
            .afterJson(scopeLabel(roleScope, normalizedGroupId))
            .result("SUCCESS")
            .build());
        return saved;
    }

    private Long validateScopeTarget(Long roleId, RoleScope roleScope, Long roleGroupId) {
        if (roleScope != RoleScope.GROUP) {
            if (roleGroupId != null) {
                throw new BizException("ROLE_SCOPE_GROUP_MISMATCH", "非组角色不能设置角色组ID");
            }
            return null;
        }
        if (roleGroupId == null) {
            throw new BizException("ROLE_GROUP_REQUIRED", "组角色必须指定所属角色组");
        }
        roleGroupRepository.findById(roleGroupId)
            .orElseThrow(() -> new BizException("ROLE_GROUP_NOT_FOUND", "角色组不存在"));
        if (!roleRepository.findPermissionIdsByRoleId(roleId).isEmpty()) {
            throw new BizException("GROUP_ROLE_PERMISSION_FORBIDDEN", "绑定平台权限的角色不能切换为组角色");
        }
        return roleGroupId;
    }

    private void requirePlatformAdmin(AuthenticatedUser principal) {
        if (!authorizationService.isPlatformAdmin(principal)) {
            throw new BizException("ROLE_SCOPE_FORBIDDEN", "只有平台管理员可以维护角色作用域");
        }
    }

    private String scopeLabel(RoleScope roleScope, Long roleGroupId) {
        RoleScope normalizedScope = roleScope == null ? RoleScope.SYSTEM : roleScope;
        return normalizedScope.name() + ":" + (roleGroupId == null ? "" : roleGroupId);
    }
}
