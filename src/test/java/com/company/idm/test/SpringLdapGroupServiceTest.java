package com.company.idm.test;

import com.company.idm.infrastructure.config.AppLdapProperties;
import com.company.idm.infrastructure.ldap.LdapDnHelper;
import com.company.idm.infrastructure.ldap.SpringLdapGroupService;
import java.util.List;
import javax.naming.Name;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.ModificationItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQuery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Spring LDAP 分组目录服务在最后一个成员删除场景下的行为。
 */
@ExtendWith(MockitoExtension.class)
class SpringLdapGroupServiceTest {

    @Mock
    private LdapTemplate ldapTemplate;

    @Test
    void shouldKeepPlaceholderMemberWhenRemovingLastRealUserFromGroup() {
        AppLdapProperties properties = buildProperties();
        SpringLdapGroupService service = new SpringLdapGroupService(ldapTemplate, properties);
        DirContextAdapter context = buildGroupContext(properties, "D001", "研发中心", "zhangsan");

        when(ldapTemplate.search(
            any(LdapQuery.class),
            org.mockito.ArgumentMatchers.<ContextMapper<DirContextAdapter>>any()
        )).thenReturn(List.of(context));

        service.removeUserFromGroup("zhangsan", "D001");

        verify(ldapTemplate).modifyAttributes(context);
        ModificationItem[] items = context.getModificationItems();
        String placeholderDn = LdapDnHelper.buildPlaceholderMemberDn(properties);

        assertThat(items).anySatisfy(item -> {
            if (!"member".equalsIgnoreCase(item.getAttribute().getID())) {
                return;
            }
            assertThat(item.getAttribute().contains(placeholderDn)).isTrue();
        });
    }

    private AppLdapProperties buildProperties() {
        AppLdapProperties properties = new AppLdapProperties();
        properties.setBaseDn("dc=corp,dc=local");
        properties.setPeopleOu("ou=people");
        properties.setGroupsOu("ou=groups");
        return properties;
    }

    private DirContextAdapter buildGroupContext(AppLdapProperties properties, String groupCode, String groupName, String username) {
        Name dn = LdapDnHelper.buildRelativeGroupDn(properties, groupCode, groupName);
        BasicAttributes attributes = new BasicAttributes();
        attributes.put(new BasicAttribute("objectClass", "groupOfNames"));
        attributes.put(new BasicAttribute("businessCategory", groupCode));
        attributes.put(new BasicAttribute("description", groupName));
        attributes.put(new BasicAttribute("cn", LdapDnHelper.buildGroupCn(groupCode, groupName)));
        attributes.put(new BasicAttribute("member", LdapDnHelper.buildUserDn(properties, username)));
        DirContextAdapter context = new DirContextAdapter(attributes, dn);
        context.setUpdateMode(true);
        return context;
    }
}
