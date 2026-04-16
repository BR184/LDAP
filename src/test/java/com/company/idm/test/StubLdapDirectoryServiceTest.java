package com.company.idm.test;

import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.domain.user.User;
import com.company.idm.infrastructure.config.AppLdapProperties;
import com.company.idm.infrastructure.ldap.StubLdapDirectoryService;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 LDAP 桩实现行为的单元测试。
 */
class StubLdapDirectoryServiceTest {

    @Test
    void shouldManageStubUsers() {
        AppLdapProperties properties = new AppLdapProperties();
        properties.setBaseDn("dc=corp,dc=local");
        properties.setPeopleOu("ou=people");
        properties.setStubUsers(Map.of("admin", "admin123456"));

        StubLdapDirectoryService service = new StubLdapDirectoryService(properties);
        service.init();

        assertThat(service.authenticate("admin", "admin123456")).isTrue();
        assertThat(service.existsByUid("admin")).isTrue();

        User user = User.builder()
            .id(2L)
            .username("zhangsan")
            .realName("张三")
            .status(UserStatus.ENABLED)
            .sourceType(SourceType.MANUAL)
            .tokenVersion(0)
            .roleCodes(Set.of())
            .build();
        String dn = service.createUser(user, "Password@123");
        assertThat(dn).isEqualTo("uid=zhangsan,ou=people,dc=corp,dc=local");
        assertThat(service.authenticate("zhangsan", "Password@123")).isTrue();

        service.disableUser("zhangsan");
        assertThat(service.authenticate("zhangsan", "Password@123")).isFalse();

        service.enableUser("zhangsan");
        service.resetPassword("zhangsan", "NewPassword@123");
        assertThat(service.authenticate("zhangsan", "NewPassword@123")).isTrue();
    }
}

