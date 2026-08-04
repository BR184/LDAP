package com.company.idm.interfaces.auth;

import com.company.idm.application.auth.AuthApplicationService;
import com.company.idm.application.auth.LoginCommand;
import com.company.idm.application.auth.LoginResult;
import com.company.idm.application.rbac.EffectivePermissionService;
import com.company.idm.application.user.ForgotPasswordCommand;
import com.company.idm.application.user.PasswordResetApplicationService;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.domain.user.User;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供登录与当前用户信息接口。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authApplicationService;
    private final PasswordResetApplicationService passwordResetApplicationService;
    private final EffectivePermissionService effectivePermissionService;

    @PostMapping("/login")
    public ApiResponse<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        LoginResult result = authApplicationService.login(new LoginCommand(request.loginId(), request.password()));
        return ApiResponse.success(new AuthLoginResponse(
            result.id(),
            result.userId(),
            result.roleCodes(),
            result.accessToken(),
            result.expiresAt()
        ));
    }

    @PostMapping("/password/forgot")
    public ApiResponse<String> forgotPassword(
        @Valid @RequestBody ForgotPasswordRequest request,
        HttpServletRequest httpServletRequest
    ) {
        passwordResetApplicationService.forgotPassword(new ForgotPasswordCommand(
            request.loginId(),
            ClientIpUtil.getClientIp(httpServletRequest)
        ));
        return ApiResponse.success(passwordResetApplicationService.forgotPasswordSuccessNotice());
    }

    @GetMapping("/me")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'AUTH_ME')")
    public ApiResponse<CurrentUserResponse> me(@AuthenticationPrincipal(expression = "userId") String username) {
        User user = authApplicationService.loadProfile(username);
        return ApiResponse.success(new CurrentUserResponse(
            user.getId(),
            user.getUserId(),
            user.getRealName(),
            user.getEmail(),
            user.getIntranetEmail(),
            user.getMobile(),
            user.getEmployeeNo(),
            user.getJobTitle(),
            user.getDeptCode(),
            user.getDeptName(),
            user.getDepartmentPath(),
            user.getRoleCodes()
        ));
    }

    @GetMapping("/capabilities")
    @PreAuthorize("@credentialAccessService.isSession(authentication)")
    public ApiResponse<CurrentCapabilitiesResponse> capabilities(
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(new CurrentCapabilitiesResponse(
            effectivePermissionService.resolve(principal).stream().sorted().toList()
        ));
    }

}

