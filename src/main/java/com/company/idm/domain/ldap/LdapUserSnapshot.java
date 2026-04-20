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

    private String username;
    private String realName;
    private String email;
    private String mobile;
    private String employeeNo;
    private String deptCode;
    private String status;
    private String dn;
}
