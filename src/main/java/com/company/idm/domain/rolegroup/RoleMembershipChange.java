package com.company.idm.domain.rolegroup;

import com.company.idm.domain.rbac.RoleScope;
import java.time.LocalDateTime;

/**
 * 角色供给事件（对外授权事实变化）。
 *
 * <p>成员类事件同时携带角色与成员身份；角色目录、角色组与订阅控制类事件只填写与自身相关的字段。
 * {@code scopeVersion} 是该事件所属角色组范围的提交有序版本，消费方据此判断新旧与补偿完整性；
 * 订阅控制事件使用 {@code controlVersion} 独立判定有效性，不参与组范围版本序列。
 *
 * @param id 事件稳定标识（第三方按此幂等）
 * @param eventType 事件类型
 * @param roleId 角色稳定标识；同编码删除再建是新角色，不得继承旧事件水位
 * @param roleCode 角色编码（业务映射契约，名称改动不改变身份）
 * @param roleName 角色名称快照
 * @param roleScope 角色作用域快照
 * @param roleGroupId 事件所属角色组范围；全局角色事件为空
 * @param scopeVersion 所属范围的提交有序版本；非组范围事件为空
 * @param userId 成员用户主键
 * @param memberName 成员姓名快照
 * @param memberUserId 成员平台用户 ID（等于 LDAP uid 与下游平台登录名）
 * @param changeType 成员变更类型 ADDED/REMOVED
 * @param payload 结构化变更内容（JSON 文本），承载角色状态、组信息与订阅控制细节
 * @param subscriptionId 订阅控制事件的目标订阅
 * @param controlVersion 订阅控制版本
 * @param gmtCreate 事件发生时间
 */
public record RoleMembershipChange(
    Long id,
    RoleSupplyEventType eventType,
    Long roleId,
    String roleCode,
    String roleName,
    RoleScope roleScope,
    Long roleGroupId,
    Long scopeVersion,
    Long userId,
    String memberName,
    String memberUserId,
    String changeType,
    String payload,
    Long subscriptionId,
    Long controlVersion,
    LocalDateTime gmtCreate
) {
}
