package com.company.idm.application.rolegroup;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rolegroup.PushSubscription;
import com.company.idm.domain.rolegroup.PushSubscriptionRepository;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 订阅令牌解析器：按已认证令牌精确反查其唯一所属订阅。
 *
 * <p>“令牌即接入”的服务端入口——接入方只提交令牌，服务端据此定位订阅并返回其范围事实
 * 与专属连接信息。解析只信任认证主体携带的令牌标识，不接受调用方传入其他订阅标识
 * 切换查询目标；令牌主体与订阅主体必须一致，防止跨订阅读取。
 */
@Component
@RequiredArgsConstructor
public class RoleSupplySubscriptionResolver {

    private final PushSubscriptionRepository subscriptionRepository;

    /**
     * 解析令牌所属订阅（不校验启用状态）。
     *
     * @param principal 已认证主体，必须是订阅令牌
     * @return 令牌唯一绑定的订阅
     * @throws BizException 令牌类型不符、未绑定订阅或主体不一致
     */
    public PushSubscription resolve(AuthenticatedUser principal) {
        if (principal == null
            || principal.credentialType() != CredentialType.PERSONAL_ACCESS_TOKEN
            || principal.tokenSubjectType() == null
            || principal.tokenSubjectType() == PersonalAccessTokenSubjectType.USER) {
            throw new BizException("ROLE_SUPPLY_TOKEN_REQUIRED", "请使用订阅令牌访问");
        }
        if (principal.tokenSubjectType() == PersonalAccessTokenSubjectType.ROLE_GROUP
            && principal.tokenSubjectId() == null) {
            throw new BizException("ROLE_SUPPLY_TOKEN_INVALID", "角色组订阅令牌缺少固定作用域");
        }
        PushSubscription subscription = subscriptionRepository.findByAccessTokenId(principal.credentialId())
            .orElseThrow(() -> new BizException("ROLE_SUPPLY_SUBSCRIPTION_NOT_FOUND", "订阅令牌未绑定任何订阅"));
        if (principal.tokenSubjectType() != subscription.getSubjectType()
            || !Objects.equals(principal.tokenSubjectId(), subscription.getSubjectId())) {
            throw new BizException("ROLE_SUPPLY_TOKEN_INVALID", "订阅令牌与订阅主体不一致");
        }
        return subscription;
    }

    /**
     * 解析令牌所属订阅并要求其处于启用状态。
     *
     * <p>停用与撤销的订阅不得继续供应业务数据；上下文接口使用
     * {@link #resolve(AuthenticatedUser)} 以便如实返回非敏感状态。
     *
     * @param principal 已认证主体
     * @return 启用状态的订阅
     * @throws BizException 订阅非启用状态
     */
    public PushSubscription resolveEnabled(AuthenticatedUser principal) {
        PushSubscription subscription = resolve(principal);
        if (!subscription.isEnabled()) {
            throw new BizException("ROLE_SUPPLY_SUBSCRIPTION_DISABLED", "订阅已停用或撤销，无法获取角色供给数据");
        }
        return subscription;
    }

    /**
     * 订阅的角色组范围标识。
     *
     * @param subscription 订阅
     * @return 角色组订阅返回组标识；全局订阅返回空
     */
    public Long groupScopeOf(PushSubscription subscription) {
        return subscription.getSubjectType() == PersonalAccessTokenSubjectType.ROLE_GROUP
            ? subscription.getSubjectId()
            : null;
    }
}
