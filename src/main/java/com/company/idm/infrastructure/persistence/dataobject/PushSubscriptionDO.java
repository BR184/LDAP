package com.company.idm.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 映射 sys_role_push_subscription 表的订阅数据对象。 */
@Getter
@Setter
@TableName("sys_role_push_subscription")
public class PushSubscriptionDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String subjectType;
    private Long subjectId;
    private String scopeMode;
    private String status;
    private Long accessTokenId;
    private Long configVersion;
    private String mqQueue;
    private String mqUsername;
    private String mqPassword;
    private String creator;
    private String modifier;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModified;
}
