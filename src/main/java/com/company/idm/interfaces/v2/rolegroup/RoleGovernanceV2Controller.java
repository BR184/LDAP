package com.company.idm.interfaces.v2.rolegroup;

import com.company.idm.application.rolegroup.RoleGovernanceApplicationService;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.domain.rbac.Role;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.interfaces.rolegroup.GovernedRoleResponse;
import com.company.idm.interfaces.rolegroup.RoleGovernanceController.UpdateRoleScopeRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 角色治理接口。
 */
@RestController
@RequestMapping("/api/v2/role-governance/roles")
@RequiredArgsConstructor
@PreAuthorize("@casbinAccessService.hasAny(authentication, 'ROLE_SCOPE_MANAGE')")
public class RoleGovernanceV2Controller {

    private final RoleGovernanceApplicationService roleGovernanceApplicationService;

    @GetMapping
    public ApiResponseV2<List<GovernedRoleResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponseV2.ok(roleGovernanceApplicationService.listRoles(principal).stream()
            .map(this::toResponse)
            .toList());
    }

    @PutMapping("/{roleId}/scope")
    public ApiResponseV2<GovernedRoleResponse> updateScope(
        @PathVariable Long roleId,
        @Valid @RequestBody UpdateRoleScopeRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(toResponse(roleGovernanceApplicationService.updateScope(
            roleId, request.roleScope(), request.roleGroupId(), principal
        )));
    }

    private GovernedRoleResponse toResponse(Role role) {
        return new GovernedRoleResponse(
            role.getId(), role.getRoleCode(), role.getRoleName(), role.getPermissionLevel(), role.getBuiltIn(),
            role.getStatus(), role.getRemark(), role.getRoleScope(), role.getRoleGroupId()
        );
    }
}
