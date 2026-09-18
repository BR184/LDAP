package com.company.idm.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 角色供给事件数据对象：成员、角色目录、角色组与订阅控制事件共用一张事实表。 */
@Getter
@Setter
@TableName("sys_role_membership_change")
public class RoleMembershipChangeDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roleId;
    private String roleCode;
    private String roleName;
    private String roleScope;
    private Long roleGroupId;
    private Long userId;
    private String memberName;
    private String memberUserId;
    private String changeType;
    private String eventType;
    private Long scopeVersion;
    private String payload;
    private Long subscriptionId;
    private Long controlVersion;
    private String operator;
    private LocalDateTime gmtCreate;
    private LocalDateTime publishedAt;
}
