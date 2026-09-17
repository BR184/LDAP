package com.company.idm.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_role_push_subscription_role")
public class PushSubscriptionRoleDO {

    private Long subscriptionId;
    private Long roleId;
    private LocalDateTime gmtCreate;
}
