package com.company.idm.interfaces.user;

import com.company.idm.application.user.ChangePasswordCommand;
import com.company.idm.application.user.BatchDeleteUsersCommand;
import com.company.idm.application.user.BatchDeleteUsersResult;
import com.company.idm.application.user.CreateUserCommand;
import com.company.idm.application.user.DeleteUserCommand;
import com.company.idm.application.user.AdminResetPasswordCommand;
import com.company.idm.application.user.PasswordResetApplicationService;
import com.company.idm.application.user.UpdateUserCommand;
import com.company.idm.application.user.UpdateUserStatusCommand;
import com.company.idm.application.user.UserApplicationService;
import com.company.idm.application.rbac.AssignUserRolesCommand;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.application.sync.SyncBatchDetail;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.common.enums.SyncRunStatus;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.common.exception.BizException;
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
 * 鎻愪緵鐢ㄦ埛鏌ヨ銆佸垱寤恒€佹洿鏂般€佸垹闄ゃ€佺姸鎬佸彉鏇村拰瀵嗙爜绠＄悊鎺ュ彛銆? */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserApplicationService userApplicationService;
    private final PasswordResetApplicationService passwordResetApplicationService;
    private final RbacApplicationService rbacApplicationService;
    private final SyncApplicationService syncApplicationService;
    private final SyncResponseAssembler syncResponseAssembler;

    @GetMapping
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users', 'GET')")
    public ApiResponse<List<UserResponse>> list(
        @RequestParam(required = false) String userId,
        @RequestParam(required = false) String deptName,
        @RequestParam(required = false) String deptCode,
        @RequestParam(required = false) Integer status
    ) {
        String departmentKeyword = deptName != null && !deptName.isBlank() ? deptName : deptCode;
        List<UserResponse> users = userApplicationService.listUsers(userId, departmentKeyword, status).stream()
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
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        User user = userApplicationService.createUser(new CreateUserCommand(
            request.userId(),
            request.realName(),
            request.email(),
            request.intranetEmail(),
            request.mobile(),
            request.employeeNo(),
            request.deptCode(),
            request.partTimeDeptCodes(),
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
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        User user = userApplicationService.updateUser(new UpdateUserCommand(
            id,
            request.userId(),
            request.realName(),
            request.email(),
            request.intranetEmail(),
            request.mobile(),
            request.employeeNo(),
            request.deptCode(),
            request.partTimeDeptCodes(),
            username
        ));
        return ApiResponse.success(toResponse(user));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id + '/status', 'PUT')")
    public ApiResponse<Void> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserStatusRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        userApplicationService.updateStatus(new UpdateUserStatusCommand(id, request.statusCode(), username));
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id, 'DELETE')")
    public ApiResponse<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        userApplicationService.deleteUser(new DeleteUserCommand(id, username));
        return ApiResponse.success();
    }

    @PostMapping("/batch-delete")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/batch-delete', 'POST')")
    public ApiResponse<BatchDeleteUsersResponse> batchDelete(
        @Valid @RequestBody BatchDeleteUsersRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        BatchDeleteUsersResult result = userApplicationService.batchDeleteUsers(new BatchDeleteUsersCommand(
            request.userIds(),
            request.userIdKeyword(),
            request.deptNameKeyword(),
            request.statusCode(),
            username
        ));
        return ApiResponse.success(new BatchDeleteUsersResponse(result.totalCount(), result.deletedCount()));
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
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
    public ApiResponse<Void> resetPassword(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        passwordResetApplicationService.adminResetPassword(new AdminResetPasswordCommand(id, username));
        return ApiResponse.success();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id + '/roles', 'PUT')")
    public ApiResponse<Void> assignRoles(
        @PathVariable Long id,
        @Valid @RequestBody AssignUserRolesRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        rbacApplicationService.assignUserRoles(new AssignUserRolesCommand(id, request.roleIds(), username));
        return ApiResponse.success();
    }

    @PostMapping("/sync/feishu")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/sync/feishu', 'POST')")
    public ApiResponse<SyncBatchDetailResponse> syncFeishu(
        @RequestBody(required = false) FeishuSyncRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponse.success(syncResponseAssembler.toResponse(
            syncApplicationService.executeFeishuUserSync(username, SyncTriggerMode.MANUAL)
        ));
    }

    @PostMapping("/import/feishu-file")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/import/feishu-file', 'POST')")
    public ApiResponse<SyncBatchDetailResponse> importFeishuFile(
        @Valid @RequestBody FeishuFileImportRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        SyncBatchDetail detail = syncApplicationService.executeFeishuUserFileImport(
            request.documentPath(),
            Boolean.TRUE.equals(request.forceFullSync()),
            request.remark(),
            username,
            SyncTriggerMode.MANUAL
        );
        throwIfFileImportFailed(detail);
        return ApiResponse.success(syncResponseAssembler.toResponse(detail));
    }

    @PostMapping("/{id}/sync-ldap")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id + '/sync-ldap', 'POST')")
    public ApiResponse<UserResponse> syncLdap(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponse.success(toResponse(userApplicationService.syncUserToLdap(id, username)));
    }

    private void throwIfFileImportFailed(SyncBatchDetail detail) {
        if (detail == null || detail.batch() == null || detail.batch().getStatus() != SyncRunStatus.FAIL) {
            return;
        }
        String errorMessage = detail.jobs().stream()
            .map(job -> job.getErrorMessage())
            .filter(message -> message != null && !message.isBlank())
            .findFirst()
            .orElse("鐢ㄦ埛鏂囦欢瀵煎叆澶辫触");
        throw new BizException("USER_FILE_IMPORT_FAILED", errorMessage);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(
            user.getId(),
            user.getUserId(),
            user.getRealName(),
            user.getEmail(),
            user.getIntranetEmail(),
            user.getMobile(),
            user.getEmployeeNo(),
            user.getDeptName(),
            user.getDeptCode(),
            user.getJobTitle(),
            user.getDirectLeaderRaw(),
            user.getLeaderRef(),
            user.getAccountStatus(),
            user.getPartTimeDeptCodes(),
            user.getPartTimeDeptNames(),
            user.getPermissionLevel(),
            user.getStatus().getCode(),
            user.getEmploymentStatus() == null ? null : user.getEmploymentStatus().name(),
            user.getLdapDn(),
            user.getRoleCodes()
        );
    }
}

