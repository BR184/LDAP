package com.company.idm.interfaces.v2.token;

import com.company.idm.application.token.CreatePersonalAccessTokenCommand;
import com.company.idm.application.token.CreatedPersonalAccessToken;
import com.company.idm.application.token.PersonalAccessTokenApplicationService;
import com.company.idm.application.token.PersonalAccessTokenPage;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.util.ClientIpUtil;
import com.company.idm.interfaces.token.AvailableTokenPermissionGroupResponse;
import com.company.idm.interfaces.token.AvailableTokenPermissionResponse;
import com.company.idm.interfaces.token.CreatePersonalAccessTokenRequest;
import com.company.idm.interfaces.token.CreatedPersonalAccessTokenResponse;
import com.company.idm.interfaces.token.PersonalAccessTokenPageResponse;
import com.company.idm.interfaces.token.PersonalAccessTokenResponse;
import com.company.idm.interfaces.token.PersonalAccessTokenSecretResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

/**
 * V2 个人访问令牌接口。
 */
@RestController
@RequestMapping("/api/v2/personal-access-tokens")
@RequiredArgsConstructor
@PreAuthorize("@credentialAccessService.isSession(authentication)")
public class PersonalAccessTokenV2Controller {

    private final PersonalAccessTokenApplicationService applicationService;

    @GetMapping
    public ApiResponseV2<PersonalAccessTokenPageResponse> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        PersonalAccessTokenPage result = applicationService.list(principal, page, pageSize);
        LocalDateTime now = LocalDateTime.now();
        return ApiResponseV2.ok(new PersonalAccessTokenPageResponse(
            result.items().stream().map(token -> PersonalAccessTokenResponse.from(token, now)).toList(),
            result.total(),
            result.page(),
            result.pageSize()
        ));
    }

    @GetMapping("/available-permissions")
    public ApiResponseV2<List<AvailableTokenPermissionResponse>> availablePermissions(
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(applicationService.availablePermissions(principal).stream()
            .map(AvailableTokenPermissionResponse::from)
            .toList());
    }

    @GetMapping("/available-permission-groups")
    public ApiResponseV2<List<AvailableTokenPermissionGroupResponse>> availablePermissionGroups(
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(applicationService.availablePermissionGroups(principal).stream()
            .map(AvailableTokenPermissionGroupResponse::from)
            .toList());
    }

    @PostMapping
    public ApiResponseV2<CreatedPersonalAccessTokenResponse> create(
        @Valid @RequestBody CreatePersonalAccessTokenRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest httpServletRequest
    ) {
        CreatedPersonalAccessToken created = applicationService.create(
            principal,
            new CreatePersonalAccessTokenCommand(
                request.name(),
                request.description(),
                request.expiresAt(),
                request.scopeMode(),
                request.permissionIds(),
                ClientIpUtil.getClientIp(httpServletRequest)
            )
        );
        return ApiResponseV2.ok(new CreatedPersonalAccessTokenResponse(
            PersonalAccessTokenResponse.from(created.token(), LocalDateTime.now()),
            created.secret()
        ));
    }

    @DeleteMapping("/{id}")
    public ApiResponseV2<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest httpServletRequest
    ) {
        applicationService.delete(principal, id, ClientIpUtil.getClientIp(httpServletRequest));
        return ApiResponseV2.ok();
    }

    @GetMapping("/{id}/secret")
    public ApiResponseV2<PersonalAccessTokenSecretResponse> reveal(
        @PathVariable Long id,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse
    ) {
        httpServletResponse.setHeader("Cache-Control", "no-store");
        httpServletResponse.setHeader("Pragma", "no-cache");
        httpServletResponse.setDateHeader("Expires", 0);
        return ApiResponseV2.ok(new PersonalAccessTokenSecretResponse(
            applicationService.reveal(principal, id, ClientIpUtil.getClientIp(httpServletRequest))
        ));
    }

    @PostMapping("/{id}/rotations")
    public ApiResponseV2<CreatedPersonalAccessTokenResponse> rotate(
        @PathVariable Long id,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest httpServletRequest
    ) {
        CreatedPersonalAccessToken rotated = applicationService.rotate(
            principal,
            id,
            ClientIpUtil.getClientIp(httpServletRequest)
        );
        return ApiResponseV2.ok(new CreatedPersonalAccessTokenResponse(
            PersonalAccessTokenResponse.from(rotated.token(), LocalDateTime.now()),
            rotated.secret()
        ));
    }

    @PostMapping("/{id}/revocations")
    public ApiResponseV2<Void> revoke(
        @PathVariable Long id,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest httpServletRequest
    ) {
        applicationService.revoke(principal, id, ClientIpUtil.getClientIp(httpServletRequest));
        return ApiResponseV2.ok();
    }
}
