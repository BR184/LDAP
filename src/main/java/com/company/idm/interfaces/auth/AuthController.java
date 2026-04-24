package com.company.idm.interfaces.auth;

import com.company.idm.application.auth.AuthApplicationService;
import com.company.idm.application.auth.LoginCommand;
import com.company.idm.application.auth.LoginResult;
import com.company.idm.application.user.ForgotPasswordCommand;
import com.company.idm.application.user.PasswordResetApplicationService;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.domain.user.User;
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

    @PostMapping("/login")
    public ApiResponse<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        LoginResult result = authApplicationService.login(new LoginCommand(request.username(), request.password()));
        return ApiResponse.success(new AuthLoginResponse(
            result.userId(),
            result.username(),
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
            request.username(),
            resolveClientIp(httpServletRequest)
        ));
        return ApiResponse.success(passwordResetApplicationService.forgotPasswordSuccessNotice());
    }

    @GetMapping("/me")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/auth/me', 'GET')")
    public ApiResponse<CurrentUserResponse> me(@AuthenticationPrincipal(expression = "username") String username) {
        User user = authApplicationService.loadProfile(username);
        return ApiResponse.success(new CurrentUserResponse(
            user.getId(),
            user.getUsername(),
            user.getRealName(),
            user.getEmail(),
            user.getMobile(),
            user.getDeptCode(),
            user.getRoleCodes()
        ));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int delimiterIndex = forwardedFor.indexOf(',');
            return delimiterIndex >= 0 ? forwardedFor.substring(0, delimiterIndex).trim() : forwardedFor.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? "UNKNOWN" : remoteAddr;
    }
}
