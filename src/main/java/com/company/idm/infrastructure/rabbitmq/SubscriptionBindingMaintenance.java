package com.company.idm.infrastructure.rabbitmq;

import com.company.idm.application.rolegroup.SubscriptionApplicationService;
import com.company.idm.domain.rolegroup.PushSubscription;
import com.company.idm.domain.rolegroup.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时订阅绑定维护。
 *
 * <p>整组动态订阅的绑定是范围通配（覆盖组内当前与后续新增角色），而历史订阅可能仍持有
 * 按角色逐个绑定的旧式规则。启动时对全部启用订阅幂等确保绑定并清理废弃绑定，使升级后
 * 无需重建订阅或重签令牌即可获得动态范围；管理接口不可用时仅告警，不阻断应用启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionBindingMaintenance implements ApplicationRunner {

    private final PushSubscriptionRepository subscriptionRepository;
    private final SubscriptionApplicationService subscriptionApplicationService;

    @Override
    public void run(ApplicationArguments args) {
        for (PushSubscription subscription : subscriptionRepository.findAllEnabled()) {
            try {
                subscriptionApplicationService.ensureBindings(subscription);
            } catch (RuntimeException exception) {
                log.warn("启动时维护订阅队列绑定失败，将在下次启用或重启时重试：subscriptionId={}",
                    subscription.getId(), exception);
            }
        }
    }
}
