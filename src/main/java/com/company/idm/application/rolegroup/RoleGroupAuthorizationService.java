package com.company.idm.application.rolegroup;

import com.company.idm.application.rbac.EffectivePermissionService;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rolegroup.RoleGroupMember;
import com.company.idm.domain.rolegroup.RoleGroupMemberRole;
import com.company.idm.domain.rolegroup.RoleGroupRepository;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleGroupAuthorizationService {

    private static final Set<String> PLATFORM_ADMIN_ROLES = Set.of("ADMIN", "SUPER_ADMIN");

    private final EffectivePermissionService effectivePermissionService;
    private final RoleGroupRepository roleGroupRepository;
    private final DelegatedPermissionPairPolicy delegatedPermissionPairPolicy;

    public void requireDelegated(AuthenticatedUser principal) {
        if (isPlatformAdmin(principal)) {
            return;
        }
        if (principal == null || principal.id() == null) {
            throw forbidden("当前身份不能管理角色组");
        }
        Set<String> permissions = effectivePermissionService.resolve(principal);
        if (!delegatedPermissionPairPolicy.isDelegated(permissions)) {
            throw forbidden("当前账号未同时持有完整的委派权限");
        }
    }

    public void requireRoleManager(AuthenticatedUser principal, Long groupId) {
        if (isPlatformAdmin(principal)) {
            return;
        }
        requireDelegated(principal);
        requireMember(principal, groupId);
    }

    public void requireOwner(AuthenticatedUser principal, Long groupId) {
        if (isPlatformAdmin(principal)) {
            return;
        }
        requireDelegated(principal);
        RoleGroupMember member = requireMember(principal, groupId);
        if (member.memberRole() != RoleGroupMemberRole.OWNER) {
            throw forbidden("只有角色组所有者可以执行此操作");
        }
    }

    public boolean isPlatformAdmin(AuthenticatedUser principal) {
        if (principal == null || principal.roleCodes() == null) {
            return false;
        }
        return principal.roleCodes().stream().anyMatch(PLATFORM_ADMIN_ROLES::contains);
    }

    private RoleGroupMember requireMember(AuthenticatedUser principal, Long groupId) {
        return roleGroupRepository.findMember(groupId, principal.id())
            .orElseThrow(() -> forbidden("当前账号不是该角色组成员"));
    }

    private BizException forbidden(String message) {
        return new BizException("ROLE_GROUP_FORBIDDEN", message);
    }
}
