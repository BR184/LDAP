package com.company.idm.interfaces.v2.rolegroup;

import com.company.idm.application.token.CreatedPersonalAccessToken;
import com.company.idm.application.token.RoleSupplyTokenApplicationService;
import com.company.idm.application.token.RoleSupplyTokenPage;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.util.ClientIpUtil;
import com.company.idm.interfaces.rolegroup.CreatedRoleSupplyTokenResponse;
import com.company.idm.interfaces.rolegroup.RoleSupplyTokenPageResponse;
import com.company.idm.interfaces.rolegroup.RoleSupplyTokenResponse;
import com.company.idm.interfaces.rolegroup.RoleSupplyTokenController.RevealRoleSupplyTokenRequest;
import com.company.idm.interfaces.rolegroup.RoleSupplyTokenController.SaveRoleSupplyTokenRequest;
import com.company.idm.interfaces.rolegroup.RoleSupplyTokenController.SecretResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
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
 * V2 角色供应令牌管理接口。
 */
@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
@PreAuthorize(
    "@credentialAccessService.isSession(authentication)"
        + " and @casbinAccessService.hasAny(authentication, 'ROLE_GROUP_MANAGE')"
)
public class RoleSupplyTokenV2Controller {

    private final RoleSupplyTokenApplicationService applicationService;

    @GetMapping("/role-groups/{groupId}/tokens")
    public ApiResponseV2<RoleSupplyTokenPageResponse> listGroupTokens(
        @PathVariable Long groupId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(toResponse(applicationService.listGroupTokens(groupId, principal, page, pageSize)));
    }

    @PostMapping("/role-groups/{groupId}/tokens")
    public ApiResponseV2<CreatedRoleSupplyTokenResponse> createGroupToken(
        @PathVariable Long groupId,
        @Valid @RequestBody SaveRoleSupplyTokenRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponseV2.ok(toResponse(applicationService.createGroupToken(
            groupId,
            principal,
            request.name(),
            request.description(),
            request.expiresAt(),
            ClientIpUtil.getClientIp(servletRequest)
        )));
    }

    @PostMapping("/role-groups/{groupId}/tokens/{tokenId}/rotations")
    public ApiResponseV2<CreatedRoleSupplyTokenResponse> rotateGroupToken(
        @PathVariable Long groupId,
        @PathVariable Long tokenId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponseV2.ok(toResponse(applicationService.rotateGroupToken(
            groupId, tokenId, principal, ClientIpUtil.getClientIp(servletRequest)
        )));
    }

    @PostMapping("/role-groups/{groupId}/tokens/{tokenId}/secret-reveals")
    public ApiResponseV2<SecretResponse> revealGroupToken(
        @PathVariable Long groupId,
        @PathVariable Long tokenId,
        @Valid @RequestBody RevealRoleSupplyTokenRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponseV2.ok(new SecretResponse(applicationService.revealGroupToken(
            groupId,
            tokenId,
            request.verificationToken(),
            principal,
            ClientIpUtil.getClientIp(servletRequest)
        )));
    }

    @PostMapping("/role-groups/{groupId}/tokens/{tokenId}/revocations")
    public ApiResponseV2<Void> revokeGroupToken(
        @PathVariable Long groupId,
        @PathVariable Long tokenId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest
    ) {
        applicationService.revokeGroupToken(
            groupId, tokenId, principal, ClientIpUtil.getClientIp(servletRequest)
        );
        return ApiResponseV2.ok();
    }

    @DeleteMapping("/role-groups/{groupId}/tokens/{tokenId}")
    public ApiResponseV2<Void> deleteGroupToken(
        @PathVariable Long groupId,
        @PathVariable Long tokenId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest
    ) {
        applicationService.deleteGroupToken(
            groupId, tokenId, principal, ClientIpUtil.getClientIp(servletRequest)
        );
        return ApiResponseV2.ok();
    }

    @GetMapping("/role-supply-tokens/global")
    public ApiResponseV2<RoleSupplyTokenPageResponse> listGlobalTokens(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(toResponse(applicationService.listGlobalTokens(principal, page, pageSize)));
    }

    @PostMapping("/role-supply-tokens/global")
    public ApiResponseV2<CreatedRoleSupplyTokenResponse> createGlobalToken(
        @Valid @RequestBody SaveRoleSupplyTokenRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponseV2.ok(toResponse(applicationService.createGlobalToken(
            principal,
            request.name(),
            request.description(),
            request.expiresAt(),
            ClientIpUtil.getClientIp(servletRequest)
        )));
    }

    @PostMapping("/role-supply-tokens/global/{tokenId}/rotations")
    public ApiResponseV2<CreatedRoleSupplyTokenResponse> rotateGlobalToken(
        @PathVariable Long tokenId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponseV2.ok(toResponse(applicationService.rotateGlobalToken(
            tokenId, principal, ClientIpUtil.getClientIp(servletRequest)
        )));
    }

    @PostMapping("/role-supply-tokens/global/{tokenId}/secret-reveals")
    public ApiResponseV2<SecretResponse> revealGlobalToken(
        @PathVariable Long tokenId,
        @Valid @RequestBody RevealRoleSupplyTokenRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponseV2.ok(new SecretResponse(applicationService.revealGlobalToken(
            tokenId,
            request.verificationToken(),
            principal,
            ClientIpUtil.getClientIp(servletRequest)
        )));
    }

    @PostMapping("/role-supply-tokens/global/{tokenId}/revocations")
    public ApiResponseV2<Void> revokeGlobalToken(
        @PathVariable Long tokenId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest
    ) {
        applicationService.revokeGlobalToken(tokenId, principal, ClientIpUtil.getClientIp(servletRequest));
        return ApiResponseV2.ok();
    }

    @DeleteMapping("/role-supply-tokens/global/{tokenId}")
    public ApiResponseV2<Void> deleteGlobalToken(
        @PathVariable Long tokenId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest
    ) {
        applicationService.deleteGlobalToken(tokenId, principal, ClientIpUtil.getClientIp(servletRequest));
        return ApiResponseV2.ok();
    }

    private RoleSupplyTokenPageResponse toResponse(RoleSupplyTokenPage page) {
        LocalDateTime now = LocalDateTime.now();
        return new RoleSupplyTokenPageResponse(
            page.items().stream().map(token -> RoleSupplyTokenResponse.from(token, now)).toList(),
            page.total(),
            page.page(),
            page.pageSize()
        );
    }

    private CreatedRoleSupplyTokenResponse toResponse(CreatedPersonalAccessToken created) {
        return new CreatedRoleSupplyTokenResponse(
            RoleSupplyTokenResponse.from(created.token(), LocalDateTime.now()),
            created.secret()
        );
    }

    private void disableSecretCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }
}
