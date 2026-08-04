package com.company.idm.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_personal_access_token")
public class PersonalAccessTokenDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String tokenUid;
    private Long userId;
    private String name;
    private String description;
    private String scopeMode;
    private String secretHash;
    private String secretValue;
    private Integer hashVersion;
    private String tokenPrefix;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private LocalDateTime lastUsedAt;
    private String lastUsedIp;
    private String creator;
    private String modifier;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModified;
}
