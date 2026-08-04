package com.company.idm.domain.rolegroup;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class RoleGroup {

    private final Long id;
    private final String groupName;
    private final String remark;
    private final Integer status;
    private final String creator;
    private final String modifier;
    private final LocalDateTime gmtCreate;
    private final LocalDateTime gmtModified;
}
