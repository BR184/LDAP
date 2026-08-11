package com.company.idm.interfaces.rolegroup;

import com.company.idm.application.token.CreatedPersonalAccessToken;
import com.company.idm.application.token.RoleSupplyTokenApplicationService;
import com.company.idm.application.token.RoleSupplyTokenPage;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

@Deprecated
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize(
    "@credentialAccessService.isSession(authentication)"
        + " and @casbinAccessService.hasAny(authentication, 'ROLE_GROUP_MANAGE')"
)
public class RoleSupplyTokenController {

    private final RoleSupplyTokenApplicationService applicationService;

    @GetMapping("/role-groups/{groupId}/tokens")
    public ApiResponse<RoleSupplyTokenPageResponse> listGroupTokens(
        @PathVariable Long groupId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(toResponse(applicationService.listGroupTokens(groupId, principal, page, pageSize)));
    }

    @PostMapping("/role-groups/{groupId}/tokens")
    public ApiResponse<CreatedRoleSupplyTokenResponse> createGroupToken(
        @PathVariable Long groupId,
        @Valid @RequestBody SaveRoleSupplyTokenRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponse.success(toResponse(applicationService.createGroupToken(
            groupId,
            principal,
            request.name(),
            request.description(),
            request.expiresAt(),
            ClientIpUtil.getClientIp(servletRequest)
        )));
    }

    @PostMapping("/role-groups/{groupId}/tokens/{tokenId}/rotations")
    public ApiResponse<CreatedRoleSupplyTokenResponse> rotateGroupToken(
        @PathVariable Long groupId,
        @PathVariable Long tokenId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponse.success(toResponse(applicationService.rotateGroupToken(
            groupId, tokenId, principal, ClientIpUtil.getClientIp(servletRequest)
        )));
    }

    @PostMapping("/role-groups/{groupId}/tokens/{tokenId}/secret-reveals")
    public ApiResponse<SecretResponse> revealGroupToken(
        @PathVariable Long groupId,
        @PathVariable Long tokenId,
        @Valid @RequestBody RevealRoleSupplyTokenRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponse.success(new SecretResponse(applicationService.revealGroupToken(
            groupId,
            tokenId,
            request.verificationToken(),
            principal,
            ClientIpUtil.getClientIp(servletRequest)
        )));
    }

    @PostMapping("/role-groups/{groupId}/tokens/{tokenId}/revocations")
    public ApiResponse<Void> revokeGroupToken(
        @PathVariable Long groupId,
        @PathVariable Long tokenId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest
    ) {
        applicationService.revokeGroupToken(
            groupId, tokenId, principal, ClientIpUtil.getClientIp(servletRequest)
        );
        return ApiResponse.success();
    }

    @DeleteMapping("/role-groups/{groupId}/tokens/{tokenId}")
    public ApiResponse<Void> deleteGroupToken(
        @PathVariable Long groupId,
        @PathVariable Long tokenId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest
    ) {
        applicationService.deleteGroupToken(
            groupId, tokenId, principal, ClientIpUtil.getClientIp(servletRequest)
        );
        return ApiResponse.success();
    }

    @GetMapping("/role-supply-tokens/global")
    public ApiResponse<RoleSupplyTokenPageResponse> listGlobalTokens(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(toResponse(applicationService.listGlobalTokens(principal, page, pageSize)));
    }

    @PostMapping("/role-supply-tokens/global")
    public ApiResponse<CreatedRoleSupplyTokenResponse> createGlobalToken(
        @Valid @RequestBody SaveRoleSupplyTokenRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponse.success(toResponse(applicationService.createGlobalToken(
            principal,
            request.name(),
            request.description(),
            request.expiresAt(),
            ClientIpUtil.getClientIp(servletRequest)
        )));
    }

    @PostMapping("/role-supply-tokens/global/{tokenId}/rotations")
    public ApiResponse<CreatedRoleSupplyTokenResponse> rotateGlobalToken(
        @PathVariable Long tokenId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponse.success(toResponse(applicationService.rotateGlobalToken(
            tokenId, principal, ClientIpUtil.getClientIp(servletRequest)
        )));
    }

    @PostMapping("/role-supply-tokens/global/{tokenId}/secret-reveals")
    public ApiResponse<SecretResponse> revealGlobalToken(
        @PathVariable Long tokenId,
        @Valid @RequestBody RevealRoleSupplyTokenRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponse.success(new SecretResponse(applicationService.revealGlobalToken(
            tokenId,
            request.verificationToken(),
            principal,
            ClientIpUtil.getClientIp(servletRequest)
        )));
    }

    @PostMapping("/role-supply-tokens/global/{tokenId}/revocations")
    public ApiResponse<Void> revokeGlobalToken(
        @PathVariable Long tokenId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest
    ) {
        applicationService.revokeGlobalToken(tokenId, principal, ClientIpUtil.getClientIp(servletRequest));
        return ApiResponse.success();
    }

    @DeleteMapping("/role-supply-tokens/global/{tokenId}")
    public ApiResponse<Void> deleteGlobalToken(
        @PathVariable Long tokenId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest
    ) {
        applicationService.deleteGlobalToken(tokenId, principal, ClientIpUtil.getClientIp(servletRequest));
        return ApiResponse.success();
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

    public record SaveRoleSupplyTokenRequest(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 255) String description,
        @Future LocalDateTime expiresAt
    ) {
    }

    public record RevealRoleSupplyTokenRequest(@NotBlank String verificationToken) {
    }

    public record SecretResponse(String secret) {
    }
}
