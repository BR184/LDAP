package com.company.idm.interfaces.v2.role;

import com.company.idm.application.rbac.BatchDeleteRolesCommand;
import com.company.idm.application.rbac.BatchDeleteRolesResult;
import com.company.idm.application.rbac.CreateRoleCommand;
import com.company.idm.application.rbac.DeleteRoleCommand;
import com.company.idm.application.rbac.GrantRolePermissionsCommand;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.rbac.UpdateRoleCommand;
import com.company.idm.application.rbac.UpdateRoleStatusCommand;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rbac.Role;
import com.company.idm.interfaces.role.BatchDeleteRolesRequest;
import com.company.idm.interfaces.role.BatchDeleteRolesResponse;
import com.company.idm.interfaces.role.CreateRoleRequest;
import com.company.idm.interfaces.role.GrantRolePermissionsRequest;
import com.company.idm.interfaces.role.RoleResponse;
import com.company.idm.interfaces.role.UpdateRoleRequest;
import com.company.idm.interfaces.role.UpdateRoleStatusRequest;
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
 * V2 角色管理接口。
 */
@RestController
@RequestMapping("/api/v2/roles")
@RequiredArgsConstructor
public class RoleV2Controller {

    private final RbacApplicationService rbacApplicationService;

    @GetMapping
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'ROLE_READ')")
    public ApiResponseV2<List<RoleResponse>> list() {
        List<RoleResponse> roles = rbacApplicationService.listRoles().stream()
            .map(this::toResponse)
            .toList();
        return ApiResponseV2.ok(roles);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'ROLE_DETAIL')")
    public ApiResponseV2<RoleResponse> detail(@PathVariable Long id) {
        return ApiResponseV2.ok(toResponse(rbacApplicationService.getRole(id)));
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'ROLE_PERMISSION_READ_BINDINGS')")
    public ApiResponseV2<List<Long>> permissionIds(@PathVariable Long id) {
        return ApiResponseV2.ok(rbacApplicationService.listRolePermissionIds(id));
    }

    @PostMapping
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'ROLE_CREATE')")
    public ApiResponseV2<RoleResponse> create(
        @Valid @RequestBody CreateRoleRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        Role role = rbacApplicationService.createRole(
            new CreateRoleCommand(request.roleCode(), request.roleName(), request.permissionLevel(), request.remark()), username
        );
        return ApiResponseV2.ok(toResponse(role));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'ROLE_UPDATE')")
    public ApiResponseV2<RoleResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateRoleRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        Role role = rbacApplicationService.updateRole(
            new UpdateRoleCommand(id, request.roleName(), request.permissionLevel(), request.remark()),
            username
        );
        return ApiResponseV2.ok(toResponse(role));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'ROLE_STATUS')")
    public ApiResponseV2<Void> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateRoleStatusRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        rbacApplicationService.updateRoleStatus(new UpdateRoleStatusCommand(id, request.status(), username));
        return ApiResponseV2.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'ROLE_DELETE')")
    public ApiResponseV2<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        rbacApplicationService.deleteRole(new DeleteRoleCommand(id, username));
        return ApiResponseV2.ok();
    }

    /**
     * V2 批量删除收紧为仅显式 roleIds，禁止按查询条件批量删。
     */
    @PostMapping("/batch-delete")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'ROLE_BATCH_DELETE')")
    public ApiResponseV2<BatchDeleteRolesResponse> batchDelete(
        @Valid @RequestBody BatchDeleteRolesRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        if (request.roleIds() == null || request.roleIds().isEmpty()) {
            throw new BizException("PARAM_INVALID", "批量删除必须显式指定 roleIds");
        }
        BatchDeleteRolesResult result = rbacApplicationService.batchDeleteRoles(new BatchDeleteRolesCommand(
            request.roleIds(),
            username
        ));
        return ApiResponseV2.ok(new BatchDeleteRolesResponse(result.totalCount(), result.deletedCount()));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'ROLE_PERMISSION_ASSIGN')")
    public ApiResponseV2<Void> grantPermissions(
        @PathVariable Long id,
        @Valid @RequestBody GrantRolePermissionsRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        rbacApplicationService.grantPermissions(new GrantRolePermissionsCommand(
            id,
            request.permissionIds(),
            request.expectedPermissionIds(),
            username
        ));
        return ApiResponseV2.ok();
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
