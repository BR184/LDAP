package com.company.idm.domain.rolegroup;

import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PushSubscriptionRepository {

    PushSubscription save(PushSubscription subscription);

    Optional<PushSubscription> findById(Long subscriptionId);

    /**
     * 按订阅令牌（访问令牌）精确反查订阅。
     *
     * <p>用于“令牌即接入”：接入方只提交令牌，服务端据此定位唯一订阅并返回其
     * 专属连接信息与范围事实。调用方不得传入其他订阅标识切换查询目标。
     *
     * @param accessTokenId 订阅持有的访问令牌标识
     * @return 对应订阅；令牌未绑定订阅时为空
     */
    Optional<PushSubscription> findByAccessTokenId(Long accessTokenId);

    List<PushSubscription> findBySubject(PersonalAccessTokenSubjectType subjectType, Long subjectId);

    List<PushSubscription> findAll();

    /** 全部启用订阅：状态心跳按订阅逐个投递，因此需要枚举启用集合。 */
    List<PushSubscription> findAllEnabled();

    long countBySubject(PersonalAccessTokenSubjectType subjectType, Long subjectId);

    boolean updateStatus(
        Long subscriptionId,
        PushSubscriptionStatus status,
        String modifier,
        LocalDateTime gmtModified
    );

    boolean updateProvisioned(
        Long subscriptionId,
        Long accessTokenId,
        String mqQueue,
        String mqUsername,
        String mqPassword,
        String modifier,
        LocalDateTime gmtModified
    );

    boolean updateMqPassword(
        Long subscriptionId,
        String mqPassword,
        String modifier,
        LocalDateTime gmtModified
    );

    /**
     * 递增订阅配置控制版本并返回新版本号。
     *
     * <p>轮换、停用、启用与撤销都必须推进控制版本，使消费方能够判别控制事实新旧，
     * 不会用旧控制状态覆盖较新的生命周期结果。
     *
     * @param subscriptionId 订阅标识
     * @param modifier 操作者
     * @param gmtModified 修改时间
     * @return 递增后的控制版本；订阅不存在时返回空
     */
    Optional<Long> bumpConfigVersion(Long subscriptionId, String modifier, LocalDateTime gmtModified);

    boolean delete(Long subscriptionId);

    void replaceRoles(Long subscriptionId, List<Long> roleIds);

    List<Long> findRoleIds(Long subscriptionId);

    Map<Long, Integer> countRolesBySubscriptionIds(List<Long> subscriptionIds);
}
