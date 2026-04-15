package com.company.idm.interfaces.user;

import com.company.idm.application.user.CreateUserCommand;
import com.company.idm.application.user.UpdateUserStatusCommand;
import com.company.idm.application.user.UserApplicationService;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.domain.user.User;
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
 * 提供用户查询、创建和状态变更接口。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserApplicationService userApplicationService;

    @GetMapping
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/users', 'GET')")
    public ApiResponse<List<UserResponse>> list() {
        List<UserResponse> users = userApplicationService.listUsers().stream()
            .map(this::toResponse)
            .toList();
        return ApiResponse.success(users);
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
