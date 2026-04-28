package com.company.idm.interfaces.role;

import com.company.idm.application.rbac.BindRoleMenusCommand;
import com.company.idm.application.rbac.BatchDeleteRolesCommand;
import com.company.idm.application.rbac.BatchDeleteRolesResult;
import com.company.idm.application.rbac.CreateRoleCommand;
import com.company.idm.application.rbac.DeleteRoleCommand;
import com.company.idm.application.rbac.GrantRolePermissionsCommand;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.rbac.UpdateRoleCommand;
import com.company.idm.application.rbac.UpdateRoleStatusCommand;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.domain.rbac.Role;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供角色查询、创建、更新、删除与授权接口。
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

    @GetMapping("/{id}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/roles/' + #id, 'GET')")
    public ApiResponse<RoleResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(toResponse(rbacApplicationService.getRole(id)));
    }

    @GetMapping("/{id}/menus")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/roles/' + #id + '/menus', 'GET')")
    public ApiResponse<List<Long>> menuIds(@PathVariable Long id) {
        return ApiResponse.success(rbacApplicationService.listRoleMenuIds(id));
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/roles/' + #id + '/permissions', 'GET')")
    public ApiResponse<List<Long>> permissionIds(@PathVariable Long id) {
        return ApiResponse.success(rbacApplicationService.listRolePermissionIds(id));
    }

    @PostMapping
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/roles', 'POST')")
    public ApiResponse<RoleResponse> create(
        @Valid @RequestBody CreateRoleRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        Role role = rbacApplicationService.createRole(
            new CreateRoleCommand(request.roleCode(), request.roleName(), request.permissionLevel(), request.remark()), username
        );
        return ApiResponse.success(toResponse(role));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/roles/' + #id, 'PUT')")
    public ApiResponse<RoleResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateRoleRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        Role role = rbacApplicationService.updateRole(
            new UpdateRoleCommand(id, request.roleName(), request.permissionLevel(), request.remark()),
            username
        );
        return ApiResponse.success(toResponse(role));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/roles/' + #id + '/status', 'PUT')")
    public ApiResponse<Void> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateRoleStatusRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        rbacApplicationService.updateRoleStatus(new UpdateRoleStatusCommand(id, request.status(), username));
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/roles/' + #id, 'DELETE')")
    public ApiResponse<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        rbacApplicationService.deleteRole(new DeleteRoleCommand(id, username));
        return ApiResponse.success();
    }

    @PostMapping("/batch-delete")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/roles/batch-delete', 'POST')")
    public ApiResponse<BatchDeleteRolesResponse> batchDelete(
        @Valid @RequestBody BatchDeleteRolesRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        BatchDeleteRolesResult result = rbacApplicationService.batchDeleteRoles(new BatchDeleteRolesCommand(
            request.roleIds(),
            username
        ));
        return ApiResponse.success(new BatchDeleteRolesResponse(result.totalCount(), result.deletedCount()));
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

    @PutMapping("/{id}/menus")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/roles/' + #id + '/menus', 'PUT')")
    public ApiResponse<Void> bindMenus(
        @PathVariable Long id,
        @Valid @RequestBody BindRoleMenusRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        rbacApplicationService.bindMenus(new BindRoleMenusCommand(id, request.menuIds(), username));
        return ApiResponse.success();
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(
            role.getId(),
            role.getRoleCode(),
            role.getRoleName(),
            role.getPermissionLevel(),
            role.getBuiltIn(),
            role.getStatus(),
            role.getRemark()
        );
    }
}
