package com.company.idm.interfaces.user;

import com.company.idm.application.user.ChangePasswordCommand;
import com.company.idm.application.user.CreateUserCommand;
import com.company.idm.application.user.DeleteUserCommand;
import com.company.idm.application.user.ResetPasswordCommand;
import com.company.idm.application.user.UpdateUserCommand;
import com.company.idm.application.user.UpdateUserStatusCommand;
import com.company.idm.application.user.UserApplicationService;
import com.company.idm.application.rbac.AssignUserRolesCommand;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.domain.user.User;
import com.company.idm.interfaces.sync.FeishuFileImportRequest;
import com.company.idm.interfaces.sync.FeishuSyncRequest;
import com.company.idm.interfaces.sync.SyncBatchDetailResponse;
import com.company.idm.interfaces.sync.SyncResponseAssembler;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供用户查询、创建、更新、删除、状态变更和密码管理接口。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserApplicationService userApplicationService;
    private final RbacApplicationService rbacApplicationService;
    private final SyncApplicationService syncApplicationService;
    private final SyncResponseAssembler syncResponseAssembler;

    @GetMapping
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users', 'GET')")
    public ApiResponse<List<UserResponse>> list(
        @RequestParam(required = false) String username,
        @RequestParam(required = false) String deptCode,
        @RequestParam(required = false) Integer status
    ) {
        List<UserResponse> users = userApplicationService.listUsers(username, deptCode, status).stream()
            .map(this::toResponse)
            .toList();
        return ApiResponse.success(users);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id, 'GET')")
    public ApiResponse<UserResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(toResponse(userApplicationService.getUser(id)));
    }

    @PostMapping
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users', 'POST')")
    public ApiResponse<UserResponse> create(
        @Valid @RequestBody CreateUserRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        User user = userApplicationService.createUser(new CreateUserCommand(
            request.username(),
            request.realName(),
            request.email(),
            request.mobile(),
            request.employeeNo(),
            request.deptCode(),
            request.initialPassword(),
            request.roleIds(),
            username
        ));
        return ApiResponse.success(toResponse(user));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id, 'PUT')")
    public ApiResponse<UserResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        User user = userApplicationService.updateUser(new UpdateUserCommand(
            id,
            request.realName(),
            request.email(),
            request.mobile(),
            request.employeeNo(),
            request.deptCode(),
            username
        ));
        return ApiResponse.success(toResponse(user));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id + '/status', 'PUT')")
    public ApiResponse<Void> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserStatusRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        userApplicationService.updateStatus(new UpdateUserStatusCommand(id, request.statusCode(), username));
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id, 'DELETE')")
    public ApiResponse<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        userApplicationService.deleteUser(new DeleteUserCommand(id, username));
        return ApiResponse.success();
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        userApplicationService.changePassword(new ChangePasswordCommand(
            username,
            request.oldPassword(),
            request.newPassword(),
            request.confirmPassword()
        ));
        return ApiResponse.success();
    }

    @PutMapping("/{id}/password/reset")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id + '/password/reset', 'PUT')")
    public ApiResponse<ResetPasswordResponse> resetPassword(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        String resetPassword = userApplicationService.resetPassword(new ResetPasswordCommand(id, username));
        return ApiResponse.success(new ResetPasswordResponse(resetPassword));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id + '/roles', 'PUT')")
    public ApiResponse<Void> assignRoles(
        @PathVariable Long id,
        @Valid @RequestBody AssignUserRolesRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        rbacApplicationService.assignUserRoles(new AssignUserRolesCommand(id, request.roleIds(), username));
        return ApiResponse.success();
    }

    @PostMapping("/sync/feishu")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/sync/feishu', 'POST')")
    public ApiResponse<SyncBatchDetailResponse> syncFeishu(
        @RequestBody(required = false) FeishuSyncRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        return ApiResponse.success(syncResponseAssembler.toResponse(
            syncApplicationService.executeFeishuUserSync(username, SyncTriggerMode.MANUAL)
        ));
    }

    @PostMapping("/import/feishu-file")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/import/feishu-file', 'POST')")
    public ApiResponse<SyncBatchDetailResponse> importFeishuFile(
        @Valid @RequestBody FeishuFileImportRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        return ApiResponse.success(syncResponseAssembler.toResponse(
            syncApplicationService.executeFeishuUserFileImport(
                request.documentPath(),
                Boolean.TRUE.equals(request.forceFullSync()),
                request.remark(),
                username,
                SyncTriggerMode.MANUAL
            )
        ));
    }

    @PostMapping("/{id}/sync-ldap")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id + '/sync-ldap', 'POST')")
    public ApiResponse<UserResponse> syncLdap(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        return ApiResponse.success(toResponse(userApplicationService.syncUserToLdap(id, username)));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getRealName(),
            user.getEmail(),
            user.getMobile(),
            user.getEmployeeNo(),
            user.getDeptCode(),
            user.getStatus().getCode(),
            user.getLdapDn(),
            user.getRoleCodes()
        );
    }
}
