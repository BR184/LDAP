package com.company.idm.test;

import com.company.idm.infrastructure.config.AppLdapProperties;
import com.company.idm.infrastructure.ldap.StubLdapGroupService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 LDAP 分组桩实现行为的单元测试。
 */
class StubLdapGroupServiceTest {

    @Test
    void shouldManageGroupsAndMembers() {
        AppLdapProperties properties = new AppLdapProperties();
        properties.setBaseDn("dc=corp,dc=local");
        properties.setPeopleOu("ou=people");
        properties.setGroupsOu("ou=groups");

        StubLdapGroupService service = new StubLdapGroupService(properties);

        String groupDn = service.createGroup("D001", "研发中心");
        assertThat(groupDn).isEqualTo("cn=D001_研发中心,ou=groups,dc=corp,dc=local");

        service.addUserToGroup("zhangsan", "D001");
        service.addUserToGroup("lisi", "D001");
        assertThat(service.snapshotMembers().get("D001"))
            .containsExactly(
                "uid=zhangsan,ou=people,dc=corp,dc=local",
                "uid=lisi,ou=people,dc=corp,dc=local"
            );

        service.syncUserGroups("wangwu", List.of("D001", "D002"));
        Map<String, List<String>> snapshot = service.snapshotMembers();
        assertThat(snapshot.get("D001")).contains("uid=wangwu,ou=people,dc=corp,dc=local");
        assertThat(snapshot.get("D002")).contains("uid=wangwu,ou=people,dc=corp,dc=local");

        service.removeUserFromGroup("zhangsan", "D001");
        assertThat(service.snapshotMembers().get("D001")).doesNotContain("uid=zhangsan,ou=people,dc=corp,dc=local");

        service.removeUserFromAllGroups("wangwu");
        assertThat(service.snapshotMembers().get("D001")).doesNotContain("uid=wangwu,ou=people,dc=corp,dc=local");
        assertThat(service.snapshotMembers().get("D002")).doesNotContain("uid=wangwu,ou=people,dc=corp,dc=local");

        String renamedDn = service.updateGroup("D001", "研发平台部");
        assertThat(renamedDn).isEqualTo("cn=D001_研发平台部,ou=groups,dc=corp,dc=local");
        assertThat(service.snapshotDns().get("D001")).isEqualTo("cn=D001_研发平台部,ou=groups,dc=corp,dc=local");
    }
}
