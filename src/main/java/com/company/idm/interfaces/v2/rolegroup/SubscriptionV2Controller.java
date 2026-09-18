package com.company.idm.interfaces.v2.rolegroup;

import com.company.idm.application.rolegroup.SubscriptionApplicationService;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.util.ClientIpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 角色变更推送订阅管理接口。
 * 订阅自动编排订阅令牌与 MQ 队列资源，凭证仅在创建与轮换时一次性展示，重看需登录密码二次验证。
 */
@RestController
@RequestMapping("/api/v2")
@RequiredArgsConstructor
@PreAuthorize(
    "@credentialAccessService.isSession(authentication)"
        + " and @casbinAccessService.hasAny(authentication, 'ROLE_GROUP_MANAGE')"
)
public class SubscriptionV2Controller {

    private final SubscriptionApplicationService applicationService;

    @GetMapping("/role-groups/{groupId}/subscriptions")
    public ApiResponseV2<List<SubscriptionResponse>> listGroupSubscriptions(
        @PathVariable Long groupId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(applicationService.listGroupSubscriptions(groupId, principal).stream()
            .map(SubscriptionResponse::from)
            .toList());
    }

    /**
     * 创建角色组订阅：订阅整个角色组，不固化角色清单。
     *
     * <p>范围随组内角色动态变化，新增角色无需修改绑定或重签令牌；因此请求不再接受角色选集。
     */
    @PostMapping("/role-groups/{groupId}/subscriptions")
    public ApiResponseV2<CreatedSubscriptionResponse> createGroupSubscription(
        @PathVariable Long groupId,
        @Valid @RequestBody SaveGroupSubscriptionRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponseV2.ok(CreatedSubscriptionResponse.from(applicationService.createGroupSubscription(
            groupId,
            request.name(),
            request.description(),
            principal,
            ClientIpUtil.getClientIp(servletRequest),
            servletRequest.getServerName()
        )));
    }

    @PostMapping("/role-groups/{groupId}/subscriptions/{subscriptionId}/rotations")
    public ApiResponseV2<SubscriptionCredentialResponse> rotateGroupSubscription(
        @PathVariable Long groupId,
        @PathVariable Long subscriptionId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponseV2.ok(SubscriptionCredentialResponse.from(applicationService.rotateSubscription(
            subscriptionId,
            principal,
            ClientIpUtil.getClientIp(servletRequest),
            servletRequest.getServerName()
        )));
    }

    @PostMapping("/role-groups/{groupId}/subscriptions/{subscriptionId}/secret-reveals")
    public ApiResponseV2<SubscriptionCredentialResponse> revealGroupSubscription(
        @PathVariable Long groupId,
        @PathVariable Long subscriptionId,
        @Valid @RequestBody RevealSubscriptionRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponseV2.ok(SubscriptionCredentialResponse.from(applicationService.revealSubscription(
            subscriptionId,
            request.verificationToken(),
            principal,
            ClientIpUtil.getClientIp(servletRequest),
            servletRequest.getServerName()
        )));
    }

    @PostMapping("/role-groups/{groupId}/subscriptions/{subscriptionId}/disables")
    public ApiResponseV2<Void> disableGroupSubscription(
        @PathVariable Long groupId,
        @PathVariable Long subscriptionId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest
    ) {
        applicationService.disableSubscription(subscriptionId, principal, ClientIpUtil.getClientIp(servletRequest));
        return ApiResponseV2.ok();
    }

    @PostMapping("/role-groups/{groupId}/subscriptions/{subscriptionId}/enables")
    public ApiResponseV2<Void> enableGroupSubscription(
        @PathVariable Long groupId,
        @PathVariable Long subscriptionId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest
    ) {
        applicationService.enableSubscription(subscriptionId, principal, ClientIpUtil.getClientIp(servletRequest));
        return ApiResponseV2.ok();
    }

    @DeleteMapping("/role-groups/{groupId}/subscriptions/{subscriptionId}")
    public ApiResponseV2<Void> deleteGroupSubscription(
        @PathVariable Long groupId,
        @PathVariable Long subscriptionId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest
    ) {
        applicationService.deleteSubscription(subscriptionId, principal, ClientIpUtil.getClientIp(servletRequest));
        return ApiResponseV2.ok();
    }

    @GetMapping("/role-supply-subscriptions/global")
    public ApiResponseV2<List<SubscriptionResponse>> listGlobalSubscriptions(
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(applicationService.listGlobalSubscriptions(principal).stream()
            .map(SubscriptionResponse::from)
            .toList());
    }

    @PostMapping("/role-supply-subscriptions/global")
    public ApiResponseV2<CreatedSubscriptionResponse> createGlobalSubscription(
        @Valid @RequestBody SaveSubscriptionRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponseV2.ok(CreatedSubscriptionResponse.from(applicationService.createGlobalSubscription(
            request.name(),
            request.description(),
            request.roleIds(),
            principal,
            ClientIpUtil.getClientIp(servletRequest),
            servletRequest.getServerName()
        )));
    }

    @PostMapping("/role-supply-subscriptions/global/{subscriptionId}/rotations")
    public ApiResponseV2<SubscriptionCredentialResponse> rotateGlobalSubscription(
        @PathVariable Long subscriptionId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponseV2.ok(SubscriptionCredentialResponse.from(applicationService.rotateSubscription(
            subscriptionId,
            principal,
            ClientIpUtil.getClientIp(servletRequest),
            servletRequest.getServerName()
        )));
    }

    @PostMapping("/role-supply-subscriptions/global/{subscriptionId}/secret-reveals")
    public ApiResponseV2<SubscriptionCredentialResponse> revealGlobalSubscription(
        @PathVariable Long subscriptionId,
        @Valid @RequestBody RevealSubscriptionRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        disableSecretCaching(servletResponse);
        return ApiResponseV2.ok(SubscriptionCredentialResponse.from(applicationService.revealSubscription(
            subscriptionId,
            request.verificationToken(),
            principal,
            ClientIpUtil.getClientIp(servletRequest),
            servletRequest.getServerName()
        )));
    }

    @PostMapping("/role-supply-subscriptions/global/{subscriptionId}/disables")
    public ApiResponseV2<Void> disableGlobalSubscription(
        @PathVariable Long subscriptionId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest
    ) {
        applicationService.disableSubscription(subscriptionId, principal, ClientIpUtil.getClientIp(servletRequest));
        return ApiResponseV2.ok();
    }

    @PostMapping("/role-supply-subscriptions/global/{subscriptionId}/enables")
    public ApiResponseV2<Void> enableGlobalSubscription(
        @PathVariable Long subscriptionId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest
    ) {
        applicationService.enableSubscription(subscriptionId, principal, ClientIpUtil.getClientIp(servletRequest));
        return ApiResponseV2.ok();
    }

    @DeleteMapping("/role-supply-subscriptions/global/{subscriptionId}")
    public ApiResponseV2<Void> deleteGlobalSubscription(
        @PathVariable Long subscriptionId,
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest
    ) {
        applicationService.deleteSubscription(subscriptionId, principal, ClientIpUtil.getClientIp(servletRequest));
        return ApiResponseV2.ok();
    }

    private void disableSecretCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }

    /** 角色组订阅创建请求：整组动态范围，不接受角色选集。 */
    public record SaveGroupSubscriptionRequest(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 255) String description
    ) {
    }

    /** 全局订阅创建请求：保留显式角色选集，不默认选择全部企业角色。 */
    public record SaveSubscriptionRequest(
        @NotBlank @Size(max = 64) String name,
        @Size(max = 255) String description,
        @NotEmpty List<Long> roleIds
    ) {
    }

    public record RevealSubscriptionRequest(@NotBlank String verificationToken) {
    }
}
