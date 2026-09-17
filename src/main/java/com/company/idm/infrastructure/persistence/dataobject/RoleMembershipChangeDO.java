package com.company.idm.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

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
    private String changeType;
    private String operator;
    private LocalDateTime gmtCreate;
    private LocalDateTime publishedAt;
}
