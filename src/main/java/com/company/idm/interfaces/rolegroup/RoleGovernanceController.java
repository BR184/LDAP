package com.company.idm.interfaces.rolegroup;

import com.company.idm.application.rolegroup.RoleGovernanceApplicationService;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

@RestController
@RequestMapping("/api/v1/role-governance/roles")
@RequiredArgsConstructor
@PreAuthorize("@casbinAccessService.hasAny(authentication, 'ROLE_SCOPE_MANAGE')")
public class RoleGovernanceController {

    private final RoleGovernanceApplicationService roleGovernanceApplicationService;

    @GetMapping
    public ApiResponse<List<GovernedRoleResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(roleGovernanceApplicationService.listRoles(principal).stream()
            .map(this::toResponse)
            .toList());
    }

    @PutMapping("/{roleId}/scope")
    public ApiResponse<GovernedRoleResponse> updateScope(
        @PathVariable Long roleId,
        @Valid @RequestBody UpdateRoleScopeRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(toResponse(roleGovernanceApplicationService.updateScope(
            roleId, request.roleScope(), request.roleGroupId(), principal
        )));
    }

    private GovernedRoleResponse toResponse(Role role) {
        return new GovernedRoleResponse(
            role.getId(), role.getRoleCode(), role.getRoleName(), role.getPermissionLevel(), role.getBuiltIn(),
            role.getStatus(), role.getRemark(), role.getRoleScope(), role.getRoleGroupId()
        );
    }

    public record UpdateRoleScopeRequest(@NotNull RoleScope roleScope, Long roleGroupId) {
    }
}
