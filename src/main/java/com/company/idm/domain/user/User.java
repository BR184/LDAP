package com.company.idm.domain.user;

import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 用户领域实体，描述平台用户的核心身份信息。
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Long id;
    private String username;
    private String realName;
    private String email;
    private String mobile;
    private String employeeNo;
    private String deptCode;
    private UserStatus status;
    private SourceType sourceType;
    private String externalId;
    private String ldapDn;
    private Integer tokenVersion;
    private Set<String> roleCodes;
}

