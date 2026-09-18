package com.company.idm.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 角色供给范围版本头数据对象：每个角色组一行，记录该范围已提交的最新版本。 */
@Getter
@Setter
@TableName("sys_role_scope_version")
public class RoleScopeVersionDO {

    @TableId(type = IdType.INPUT)
    private Long scopeId;
    private Long committedVersion;
    private LocalDateTime gmtModified;
}
