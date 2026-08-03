package com.company.idm.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_personal_access_token_permission")
public class PersonalAccessTokenPermissionDO {

    private Long tokenId;
    private Long permissionId;
    private LocalDateTime gmtCreate;
}
