package com.company.idm.domain.user;

import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import java.util.List;
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
    private String userId;
    private String realName;
    private String email;
    private String intranetEmail;
    private String mobile;
    private String employeeNo;
    private String deptCode;
    private String deptName;
    private String jobTitle;
    private String directLeaderRaw;
    private String leaderRef;
    private String accountStatus;
    private List<String> partTimeDeptCodes;
    private List<String> partTimeDeptNames;
    private UserStatus status;
    private EmploymentStatus employmentStatus;
    private SourceType sourceType;
    private String ldapDn;
    private Integer tokenVersion;
    private Integer permissionLevel;
    private Set<String> roleCodes;

    public String getUsername() {
        return userId;
    }

    public String getExternalId() {
        return userId;
    }

    public static class UserBuilder {
        public UserBuilder username(String username) {
            this.userId = username;
            return this;
        }

        public UserBuilder externalId(String externalId) {
            this.userId = externalId;
            return this;
        }
    }
}

