package com.company.idm.domain.rolegroup;

import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色供给事件仓储。
 *
 * <p>组订阅按范围版本读取增量（版本顺序等于提交顺序，可安全用作检查点）；
 * 全局订阅沿用事件标识游标，保持既有对外契约不变。
 */
public interface RoleMembershipChangeRepository {

    /** 全局事件游标头（最大事件标识），供全局订阅使用。 */
    long currentCursor();

    /**
     * 按事件标识游标读取增量（全局订阅语义）。
     *
     * @param cursor 上次读取到的事件标识
     * @param limit 最多返回条数
     * @param subjectType 令牌主体类型
     * @param subjectId 令牌主体标识（角色组 ID）
     * @return 按标识升序的事件列表
     */
    List<RoleMembershipChange> findAfter(
        long cursor,
        int limit,
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId
    );

    /**
     * 按范围版本读取组订阅增量：只返回该组中版本严格大于 since 的事件，按版本升序。
     *
     * @param groupId 角色组范围
     * @param sinceVersion 消费方已持久化的检查点版本
     * @param limit 最多返回条数
     * @return 版本升序事件列表
     */
    List<RoleMembershipChange> findAfterScopeVersion(Long groupId, long sinceVersion, int limit);

    /**
     * 该范围可恢复的最早版本；早于此版本的增量已超出保留窗口，消费方必须重建快照。
     *
     * @param groupId 角色组范围
     * @return 保留窗口内最小版本；无保留事件时返回 0
     */
    long earliestScopeVersion(Long groupId);

    /** 读取某订阅的控制事件（按控制版本升序），用于订阅生命周期恢复。 */
    List<RoleMembershipChange> findSubscriptionControlEvents(Long subscriptionId, int limit);

    List<RoleMembershipChange> findUnpublished(int limit);

    void markPublished(List<Long> changeIds, LocalDateTime publishedAt);

    /** 清理超出保留窗口的事件，返回删除条数；由部署配置的保留天数驱动。 */
    int deleteOlderThan(LocalDateTime threshold, int limit);
}
