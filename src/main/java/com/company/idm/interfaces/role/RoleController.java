package com.company.idm.interfaces.role;

import com.company.idm.application.rbac.CreateRoleCommand;
import com.company.idm.application.rbac.GrantRolePermissionsCommand;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.domain.rbac.Role;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供角色查询、创建与授权接口。
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RbacApplicationService rbacApplicationService;

    @GetMapping
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/roles', 'GET')")
    public ApiResponse<List<RoleResponse>> list() {
        List<RoleResponse> roles = rbacApplicationService.listRoles().stream()
            .map(this::toResponse)
            .toList();
        return ApiResponse.success(roles);
    }

    @PostMapping
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/roles', 'POST')")
    public ApiResponse<RoleResponse> create(
        @Valid @RequestBody CreateRoleRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        Role role = rbacApplicationService.createRole(
            new CreateRoleCommand(request.roleCode(), request.roleName(), request.remark()), username
        );
        return ApiResponse.success(toResponse(role));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/roles/' + #id + '/permissions', 'PUT')")
    public ApiResponse<Void> grantPermissions(
        @PathVariable Long id,
        @Valid @RequestBody GrantRolePermissionsRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        rbacApplicationService.grantPermissions(new GrantRolePermissionsCommand(id, request.permissionIds(), username));
        return ApiResponse.success();
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(role.getId(), role.getRoleCode(), role.getRoleName(), role.getStatus(), role.getRemark());
    }
}
