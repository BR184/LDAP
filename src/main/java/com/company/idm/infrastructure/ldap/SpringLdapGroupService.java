package com.company.idm.infrastructure.ldap;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.infrastructure.config.AppLdapProperties;
import java.util.List;
import javax.naming.Name;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Service;

/**
 * 基于 Spring LDAP 实现真实 LDAP 分组目录操作。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ldap", name = "mode", havingValue = "spring")
public class SpringLdapGroupService implements LdapGroupService {

    private final LdapTemplate ldapTemplate;
    private final AppLdapProperties ldapProperties;

    @Override
    public String createGroup(String groupCode, String groupName) {
        if (existsGroup(groupCode)) {
            updateGroup(groupCode, groupName);
            return buildGroupDn(groupCode, groupName).toString();
        }
        Name dn = buildGroupDn(groupCode, groupName);
        BasicAttributes attributes = new BasicAttributes();
        attributes.put("objectClass", "groupOfNames");
        attributes.put(new BasicAttribute("cn", buildGroupCn(groupCode, groupName)));
        attributes.put(new BasicAttribute("description", groupName));
        attributes.put(new BasicAttribute("businessCategory", groupCode));
        // groupOfNames 要求至少存在一个 member，这里先使用占位 DN，待首次加人后会保留或覆盖。
        attributes.put(new BasicAttribute("member", buildPlaceholderMemberDn()));
        ldapTemplate.bind(dn, null, attributes);
        return dn.toString();
    }

    @Override
    public String updateGroup(String groupCode, String groupName) {
        if (!existsGroup(groupCode)) {
            return createGroup(groupCode, groupName);
        }
        DirContextAdapter context = lookupGroup(groupCode);
        Name expectedDn = buildGroupDn(groupCode, groupName);
        if (!context.getDn().equals(expectedDn)) {
            ldapTemplate.rename(context.getDn(), expectedDn);
            context = lookupGroup(groupCode);
        }
        context.setAttributeValue("cn", buildGroupCn(groupCode, groupName));
        context.setAttributeValue("description", groupName);
        ldapTemplate.modifyAttributes(context);
        return expectedDn.toString();
    }

    @Override
    public void deleteGroup(String groupCode) {
        if (!existsGroup(groupCode)) {
            return;
        }
        DirContextAdapter context = lookupGroup(groupCode);
        ldapTemplate.unbind(context.getDn());
    }

    @Override
    public void addUserToGroup(String username, String groupCode) {
        DirContextAdapter context = lookupGroup(groupCode);
        String userDn = buildUserDn(username);
        String placeholder = buildPlaceholderMemberDn();
        String[] members = context.getStringAttributes("member");
        boolean alreadyExists = members != null && java.util.Arrays.stream(members).anyMatch(userDn::equals);
        if (alreadyExists) {
            return;
        }
        context.addAttributeValue("member", userDn);
        if (members != null && java.util.Arrays.stream(members).anyMatch(placeholder::equals)) {
            context.removeAttributeValue("member", placeholder);
        }
        ldapTemplate.modifyAttributes(context);
    }

    @Override
    public void removeUserFromGroup(String username, String groupCode) {
        try {
            DirContextAdapter context = lookupGroup(groupCode);
            String userDn = buildUserDn(username);
            String[] members = context.getStringAttributes("member");
            if (members == null || java.util.Arrays.stream(members).noneMatch(userDn::equals)) {
                return;
            }
            context.removeAttributeValue("member", userDn);
            if (remainingMembersCount(context) == 0) {
                context.addAttributeValue("member", buildPlaceholderMemberDn());
            }
            ldapTemplate.modifyAttributes(context);
        } catch (BizException ignored) {
            // 当前阶段按幂等处理，不因为分组不存在打断主流程。
        }
    }

    @Override
    public void syncUserGroups(String username, List<String> groupCodes) {
        removeUserFromAllGroups(username);
        if (groupCodes == null) {
            return;
        }
        for (String groupCode : groupCodes) {
            addUserToGroup(username, groupCode);
        }
    }

    @Override
    public void removeUserFromAllGroups(String username) {
        String userDn = buildUserDn(username);
        ldapTemplate.search(
            LdapQueryBuilder.query().base(ldapProperties.getGroupsOu()).where("member").is(userDn),
            (AttributesMapper<String>) attributes -> attributes.get("cn").get().toString()
        ).forEach(groupCode -> removeUserFromGroup(username, groupCode));
    }

    private boolean existsGroup(String groupCode) {
        return !ldapTemplate.search(
            LdapQueryBuilder.query().base(ldapProperties.getGroupsOu()).where("businessCategory").is(groupCode),
            (AttributesMapper<String>) attributes -> attributes.get("cn") == null ? null : attributes.get("cn").get().toString()
        ).isEmpty();
    }

    private DirContextAdapter lookupGroup(String groupCode) {
        try {
            return ldapTemplate.search(
                LdapQueryBuilder.query().base(ldapProperties.getGroupsOu()).where("businessCategory").is(groupCode),
                (ContextMapper<DirContextAdapter>) ctx -> (DirContextAdapter) ctx
            ).stream().findFirst().orElseThrow(() -> new BizException("LDAP_GROUP_NOT_FOUND", "LDAP 分组不存在"));
        } catch (NameNotFoundException exception) {
            throw new BizException("LDAP_GROUP_NOT_FOUND", "LDAP 分组不存在");
        }
    }

    private int remainingMembersCount(DirContextAdapter context) {
        String[] members = context.getStringAttributes("member");
        if (members == null) {
            return 0;
        }
        String placeholder = buildPlaceholderMemberDn();
        return (int) java.util.Arrays.stream(members).filter(member -> !placeholder.equals(member)).count();
    }

    private Name buildGroupDn(String groupCode, String groupName) {
        String groupsOuValue = ldapProperties.getGroupsOu().replace("ou=", "");
        return LdapNameBuilder.newInstance(ldapProperties.getBaseDn())
            .add("ou", groupsOuValue)
            .add("cn", buildGroupCn(groupCode, groupName))
            .build();
    }

    private String buildUserDn(String username) {
        return "uid=" + username + "," + ldapProperties.getPeopleOu() + "," + ldapProperties.getBaseDn();
    }

    private String buildPlaceholderMemberDn() {
        return "uid=placeholder," + ldapProperties.getPeopleOu() + "," + ldapProperties.getBaseDn();
    }

    private String buildGroupCn(String groupCode, String groupName) {
        return groupCode + "_" + groupName;
    }
}
