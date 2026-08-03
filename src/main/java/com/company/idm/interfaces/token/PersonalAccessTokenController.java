package com.company.idm.interfaces.token;

import com.company.idm.application.token.CreatePersonalAccessTokenCommand;
import com.company.idm.application.token.CreatedPersonalAccessToken;
import com.company.idm.application.token.PersonalAccessTokenApplicationService;
import com.company.idm.application.token.PersonalAccessTokenPage;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/personal-access-tokens")
@RequiredArgsConstructor
@PreAuthorize("@credentialAccessService.isSession(authentication)")
public class PersonalAccessTokenController {

    private final PersonalAccessTokenApplicationService applicationService;

    @GetMapping
    public ApiResponse<PersonalAccessTokenPageResponse> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        PersonalAccessTokenPage result = applicationService.list(principal, page, pageSize);
        LocalDateTime now = LocalDateTime.now();
        return ApiResponse.success(new PersonalAccessTokenPageResponse(
            result.items().stream().map(token -> PersonalAccessTokenResponse.from(token, now)).toList(),
            result.total(),
            result.page(),
            result.pageSize()
        ));
    }

    @GetMapping("/available-permissions")
    public ApiResponse<List<AvailableTokenPermissionResponse>> availablePermissions(
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(applicationService.availablePermissions(principal).stream()
            .map(AvailableTokenPermissionResponse::from)
            .toList());
    }

    @PostMapping
    public ApiResponse<CreatedPersonalAccessTokenResponse> create(
        @Valid @RequestBody CreatePersonalAccessTokenRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest httpServletRequest
    ) {
        CreatedPersonalAccessToken created = applicationService.create(
            principal,
            new CreatePersonalAccessTokenCommand(
                request.name(),
                request.expiresAt(),
                request.permissionIds(),
                request.passwordVerificationToken(),
                ClientIpUtil.getClientIp(httpServletRequest)
            )
        );
        return ApiResponse.success(new CreatedPersonalAccessTokenResponse(
            PersonalAccessTokenResponse.from(created.token(), LocalDateTime.now()),
            created.secret()
        ));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> revoke(
        @PathVariable Long id,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest httpServletRequest
    ) {
        applicationService.revoke(principal, id, ClientIpUtil.getClientIp(httpServletRequest));
        return ApiResponse.success();
    }
}
