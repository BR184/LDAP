package com.company.idm.infrastructure.ldap;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.ldap.LdapGroupSnapshot;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.infrastructure.config.AppLdapProperties;
import java.util.ArrayList;
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
    public boolean existsGroup(String groupCode) {
        return !ldapTemplate.search(
            LdapQueryBuilder.query().base(ldapProperties.getGroupsOu()).where("businessCategory").is(groupCode),
            (AttributesMapper<String>) attributes -> attributes.get("cn") == null ? null : attributes.get("cn").get().toString()
        ).isEmpty();
    }

    @Override
    public String findGroupDn(String groupCode) {
        if (!existsGroup(groupCode)) {
            return null;
        }
        return LdapDnHelper.toAbsoluteDn(ldapProperties, lookupGroup(groupCode).getDn());
    }

    @Override
    public LdapGroupSnapshot findGroupSnapshot(String groupCode) {
        if (!existsGroup(groupCode)) {
            return null;
        }
        DirContextAdapter context = lookupGroup(groupCode);
        return LdapGroupSnapshot.builder()
            .groupCode(groupCode)
            .groupName(context.getStringAttribute("description"))
            .dn(LdapDnHelper.toAbsoluteDn(ldapProperties, context.getDn()))
            .members(extractMembers(context))
            .build();
    }

    @Override
    public List<String> listAllGroupCodes() {
        return ldapTemplate.search(
            LdapQueryBuilder.query().base(ldapProperties.getGroupsOu()).where("objectClass").is("groupOfNames"),
            (AttributesMapper<String>) attributes -> attributes.get("businessCategory") == null ? null : attributes.get("businessCategory").get().toString()
        ).stream()
            .filter(code -> code != null && !code.isBlank())
            .sorted()
            .toList();
    }

    @Override
    public List<String> listUserGroups(String username) {
        String userDn = LdapDnHelper.buildUserDn(ldapProperties, username);
        return ldapTemplate.search(
            LdapQueryBuilder.query().base(ldapProperties.getGroupsOu()).where("member").is(userDn),
            (AttributesMapper<String>) attributes -> attributes.get("businessCategory") == null ? null : attributes.get("businessCategory").get().toString()
        ).stream()
            .filter(code -> code != null && !code.isBlank())
            .sorted()
            .toList();
    }

    @Override
    public String createGroup(String groupCode, String groupName) {
        if (existsGroup(groupCode)) {
            updateGroup(groupCode, groupName);
            return LdapDnHelper.buildGroupDn(ldapProperties, groupCode, groupName);
        }
        Name dn = LdapDnHelper.buildRelativeGroupDn(ldapProperties, groupCode, groupName);
        BasicAttributes attributes = new BasicAttributes();
        attributes.put("objectClass", "groupOfNames");
        attributes.put(new BasicAttribute("cn", LdapDnHelper.buildGroupCn(groupCode, groupName)));
        attributes.put(new BasicAttribute("description", groupName));
        attributes.put(new BasicAttribute("businessCategory", groupCode));
        // groupOfNames 要求至少存在一个 member，这里先使用占位 DN，待首次加人后会保留或覆盖。
        attributes.put(new BasicAttribute("member", LdapDnHelper.buildPlaceholderMemberDn(ldapProperties)));
        ldapTemplate.bind(dn, null, attributes);
        return LdapDnHelper.toAbsoluteDn(ldapProperties, dn);
    }

    @Override
    public String updateGroup(String groupCode, String groupName) {
        if (!existsGroup(groupCode)) {
            return createGroup(groupCode, groupName);
        }
        DirContextAdapter context = lookupGroup(groupCode);
        Name expectedDn = LdapDnHelper.buildRelativeGroupDn(ldapProperties, groupCode, groupName);
        if (!context.getDn().equals(expectedDn)) {
            ldapTemplate.rename(context.getDn(), expectedDn);
            context = lookupGroup(groupCode);
        }
        context.setAttributeValue("cn", LdapDnHelper.buildGroupCn(groupCode, groupName));
        context.setAttributeValue("description", groupName);
        ldapTemplate.modifyAttributes(context);
        return LdapDnHelper.toAbsoluteDn(ldapProperties, expectedDn);
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
        String userDn = LdapDnHelper.buildUserDn(ldapProperties, username);
        String placeholder = LdapDnHelper.buildPlaceholderMemberDn(ldapProperties);
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
            String userDn = LdapDnHelper.buildUserDn(ldapProperties, username);
            String placeholderDn = LdapDnHelper.buildPlaceholderMemberDn(ldapProperties);
            String[] members = context.getStringAttributes("member");
            if (members == null || java.util.Arrays.stream(members).noneMatch(userDn::equals)) {
                return;
            }
            List<String> remainingMembers = java.util.Arrays.stream(members)
                .filter(member -> !userDn.equals(member))
                .filter(member -> !placeholderDn.equals(member))
                .toList();
            if (remainingMembers.isEmpty()) {
                context.setAttributeValues("member", new String[] { placeholderDn });
            } else {
                context.setAttributeValues("member", remainingMembers.toArray(String[]::new));
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
        String userDn = LdapDnHelper.buildUserDn(ldapProperties, username);
        ldapTemplate.search(
            LdapQueryBuilder.query().base(ldapProperties.getGroupsOu()).where("member").is(userDn),
            (AttributesMapper<String>) attributes -> attributes.get("businessCategory").get().toString()
        ).forEach(groupCode -> removeUserFromGroup(username, groupCode));
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

    private List<String> extractMembers(DirContextAdapter context) {
        String[] members = context.getStringAttributes("member");
        if (members == null) {
            return List.of();
        }
        List<String> usernames = new ArrayList<>();
        for (String member : members) {
            String username = LdapDnHelper.extractUid(member);
            if (username == null || "placeholder".equals(username)) {
                continue;
            }
            usernames.add(username);
        }
        return usernames.stream().sorted().toList();
    }
}
