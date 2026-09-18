package com.company.idm.application.rolegroup;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 完整快照结果。
 *
 * <p>{@code scopeVersion} 是该范围的提交有序边界：消费方在同一授权事务中提交事实与该边界，
 * 之后只落地晚于边界的增量。组订阅的边界是角色组范围版本（在版本行共享锁下取得，
 * 与内容来自同一一致视图）；全局订阅沿用事件游标头，保持既有对外语义。
 * {@code complete} 为完整性声明——超时、缺字段、缺页或部分失败都不允许标记为完整，
 * 空集合是明确事实而非失败。大快照分页传输时所有批次属于同一冻结版本。
 *
 * @param snapshotCursor 兼容字段：全局订阅为事件游标头，组订阅等于 scopeVersion
 * @param scopeType 范围类型：ROLE_GROUP 或 GLOBAL
 * @param roleGroupId 角色组范围标识；全局订阅为空
 * @param scopeVersion 范围提交有序边界
 * @param complete 本页是否属于完整一致的快照视图
 * @param page 当前页码，从 1 开始
 * @param pageSize 本页角色数上限
 * @param hasMore 是否还有后续页；接收完整前消费方不得执行撤权替换
 * @param generatedAt 生成时间
 * @param roles 本页角色事实
 */
public record RoleSupplySnapshot(
    long snapshotCursor,
    String scopeType,
    Long roleGroupId,
    long scopeVersion,
    boolean complete,
    int page,
    int pageSize,
    boolean hasMore,
    LocalDateTime generatedAt,
    List<RoleSupplyRoleSnapshot> roles
) {
}
