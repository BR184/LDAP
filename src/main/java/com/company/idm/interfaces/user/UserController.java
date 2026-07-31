package com.company.idm.interfaces.user;

import com.company.idm.application.rbac.AssignUserRolesCommand;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.department.DepartmentDisplay;
import com.company.idm.application.department.DepartmentPathService;
import com.company.idm.application.user.AdminResetPasswordCommand;
import com.company.idm.application.user.BatchDeleteUsersCommand;
import com.company.idm.application.user.BatchDeleteUsersResult;
import com.company.idm.application.user.ChangePasswordCommand;
import com.company.idm.application.user.CreateUserCommand;
import com.company.idm.application.user.DeleteUserCommand;
import com.company.idm.application.user.PasswordResetApplicationService;
import com.company.idm.application.user.PasswordResetAuthorizationService;
import com.company.idm.application.user.UpdateUserCommand;
import com.company.idm.application.user.UpdateUserAccessCommand;
import com.company.idm.application.user.UserApplicationService;
import com.company.idm.application.user.UserReadScopeService;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.infrastructure.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
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

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserApplicationService userApplicationService;
    private final PasswordResetApplicationService passwordResetApplicationService;
    private final PasswordResetAuthorizationService passwordResetAuthorizationService;
    private final UserReadScopeService userReadScopeService;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final RbacApplicationService rbacApplicationService;
    private final DepartmentRepository departmentRepository;
    private final DepartmentPathService departmentPathService;

    @GetMapping
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users', 'GET')")
    public ApiResponse<List<UserResponse>> list(
        @RequestParam(required = false) String userId,
        @RequestParam(required = false) String deptName,
        @RequestParam(required = false) String deptCode,
        @RequestParam(required = false) Boolean accessAllowed,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        String departmentKeyword = deptName != null && !deptName.isBlank() ? deptName : deptCode;
        User operator = resolveOperator(username);
        List<User> organizationSnapshot = userRepositorySnapshot();
        List<Department> departmentSnapshot = departmentRepository.findAll();
        Set<String> operatorPermissionCodes = permissionRepository.findPermissionCodesByUserId(username);
        List<User> userList = userReadScopeService.filterVisibleUsers(
            operator,
            userApplicationService.listUsers(userId, departmentKeyword, accessAllowed),
            organizationSnapshot,
            operatorPermissionCodes
        );
        List<UserResponse> users = userList.stream()
            .map(user -> toResponse(
                user,
                canResetPassword(operator, user, organizationSnapshot, operatorPermissionCodes),
                departmentSnapshot
            ))
            .toList();
        return ApiResponse.success(users);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id, 'GET')")
    public ApiResponse<UserResponse> detail(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        User user = userApplicationService.getUser(id);
        User operator = resolveOperator(username);
        List<User> organizationSnapshot = userRepositorySnapshot();
        Set<String> operatorPermissionCodes = permissionRepository.findPermissionCodesByUserId(username);
        userReadScopeService.checkCanReadUser(operator, user, organizationSnapshot, operatorPermissionCodes);
        return ApiResponse.success(toResponse(
            user,
            canResetPassword(operator, user, organizationSnapshot, operatorPermissionCodes),
            departmentRepository.findAll()
        ));
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
            request.accessAllowed(),
            request.roleIds(),
            username
        ));
        return ApiResponse.success(toResponse(user, false, departmentRepository.findAll()));
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
        return ApiResponse.success(toResponse(user, false, departmentRepository.findAll()));
    }

    @PutMapping("/{id}/access")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id + '/access', 'PUT')")
    public ApiResponse<Void> updateAccess(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserAccessRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        userApplicationService.updateAccess(new UpdateUserAccessCommand(id, request.accessAllowed(), username));
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
            request.accessAllowed(),
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
            request.verificationToken(),
            request.newPassword(),
            request.confirmPassword()
        ));
        return ApiResponse.success();
    }

    @PostMapping("/me/password/verify")
    public ApiResponse<VerifyPasswordResponse> verifyPassword(
        @Valid @RequestBody VerifyPasswordRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponse.success(new VerifyPasswordResponse(userApplicationService.verifyPassword(username, request.oldPassword())));
    }

    @PutMapping("/{id}/password/reset")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id + '/password/reset', 'PUT')")
    public ApiResponse<Void> resetPassword(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "userId") String username,
        HttpServletRequest httpServletRequest
    ) {
        passwordResetApplicationService.adminResetPassword(new AdminResetPasswordCommand(
            id,
            username,
            ClientIpUtil.getClientIp(httpServletRequest)
        ));
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

    @PostMapping("/{id}/sync-ldap")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users/' + #id + '/sync-ldap', 'POST')")
    public ApiResponse<UserResponse> syncLdap(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponse.success(toResponse(userApplicationService.syncUserToLdap(id, username), false, departmentRepository.findAll()));
    }

    private UserResponse toResponse(User user, boolean canResetPassword, List<Department> departments) {
        List<DepartmentReferenceResponse> partTimeDepartments = toDepartmentReferences(
            user.getPartTimeDeptCodes(),
            departments
        );
        List<String> partTimeDeptCodes = partTimeDepartments.stream()
            .map(DepartmentReferenceResponse::deptCode)
            .toList();
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
            user.getDepartmentPath(),
            user.getJobTitle(),
            user.getDirectLeaderRaw(),
            user.getLeaderRef(),
            user.getAccountStatus(),
            partTimeDeptCodes,
            partTimeDepartments.stream()
                .map(department -> department.deptName() == null || department.deptName().isBlank()
                    ? department.deptCode()
                    : department.deptName())
                .toList(),
            partTimeDepartments,
            user.getPermissionLevel(),
            user.isAccessAllowed(),
            user.getEmploymentStatus() == null ? null : user.getEmploymentStatus().name(),
            user.getLdapDn(),
            user.getRoleCodes(),
            canResetPassword
        );
    }

    private List<DepartmentReferenceResponse> toDepartmentReferences(List<String> departmentCodes, List<Department> departments) {
        if (departmentCodes == null || departmentCodes.isEmpty()) {
            return List.of();
        }
        return departmentCodes.stream()
            .filter(java.util.Objects::nonNull)
            .map(String::trim)
            .filter(code -> !code.isBlank())
            .distinct()
            .map(code -> {
                DepartmentDisplay display = departmentPathService.resolve(code, departments);
                return new DepartmentReferenceResponse(code, display.departmentName(), display.departmentPath());
            })
            .toList();
    }

    private User resolveOperator(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            return userRepository.findByUserId(username).orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean canResetPassword(User operator, User target, List<User> allUsers, Set<String> operatorPermissionCodes) {
        return passwordResetAuthorizationService.evaluate(operator, target, allUsers, operatorPermissionCodes).allowed();
    }

    private List<User> userRepositorySnapshot() {
        return userRepository.findAll();
    }
}
