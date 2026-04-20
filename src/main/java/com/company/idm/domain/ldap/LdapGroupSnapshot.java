package com.company.idm.domain.ldap;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 描述 LDAP 分组条目的快照信息。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LdapGroupSnapshot {

    private String groupCode;
    private String groupName;
    private String dn;
    private List<String> members;
}
