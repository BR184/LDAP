package com.company.idm.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_role_group_member")
public class RoleGroupMemberDO {

    private Long groupId;
    private Long userId;
    private String memberRole;
    private String creator;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModified;
}
