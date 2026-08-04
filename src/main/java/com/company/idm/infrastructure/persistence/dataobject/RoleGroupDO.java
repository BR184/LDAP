package com.company.idm.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_role_group")
public class RoleGroupDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String groupName;
    private String remark;
    private Integer status;
    private String creator;
    private String modifier;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModified;
}
