package com.company.idm.domain.ldap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 描述 LDAP 用户条目的快照信息。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LdapUserSnapshot {

    private String userId;
    private String realName;
    private String email;
    private String mobile;
    private String employeeNo;
    private String deptCode;
    private String status;
    private String dn;

    public String getUsername() {
        return userId;
    }

    public static class LdapUserSnapshotBuilder {
        public LdapUserSnapshotBuilder username(String username) {
            this.userId = username;
            return this;
        }
    }
}
