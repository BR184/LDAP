package com.company.idm.infrastructure.ldap;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.user.User;
import com.company.idm.infrastructure.config.AppLdapProperties;
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
 * 基于 Spring LDAP 实现真实目录服务操作。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ldap", name = "mode", havingValue = "spring")
public class SpringLdapDirectoryService implements LdapDirectoryService {

    private final LdapTemplate ldapTemplate;
    private final AppLdapProperties ldapProperties;

    @Override
    public boolean authenticate(String username, String password) {
        try {
            ldapTemplate.authenticate(
                LdapQueryBuilder.query().base(ldapProperties.getPeopleOu()).where("uid").is(username),
                password
            );
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean existsByUid(String username) {
        return !ldapTemplate.search(
            LdapQueryBuilder.query().base(ldapProperties.getPeopleOu()).where("uid").is(username),
            (AttributesMapper<String>) attributes -> attributes.get("uid") == null ? null : attributes.get("uid").get().toString()
        ).isEmpty();
    }

    @Override
    public String createUser(User user, String rawPassword) {
        String peopleOuValue = ldapProperties.getPeopleOu().replace("ou=", "");
        Name dn = LdapNameBuilder.newInstance(ldapProperties.getBaseDn())
            .add("ou", peopleOuValue)
            .add("uid", user.getUsername())
            .build();
        BasicAttributes attributes = new BasicAttributes();
        attributes.put("objectClass", "inetOrgPerson");
        attributes.put(new BasicAttribute("uid", user.getUsername()));
        attributes.put(new BasicAttribute("cn", user.getRealName()));
        attributes.put(new BasicAttribute("sn", user.getRealName()));
        putIfPresent(attributes, "mail", user.getEmail());
        putIfPresent(attributes, "mobile", user.getMobile());
        putIfPresent(attributes, "employeeNumber", user.getEmployeeNo());
        putIfPresent(attributes, "departmentNumber", user.getDeptCode());
        attributes.put(new BasicAttribute("employeeType", "ENABLED"));
        attributes.put(new BasicAttribute("userPassword", rawPassword));
        ldapTemplate.bind(dn, null, attributes);
        return dn.toString();
    }

    @Override
    public void updateUser(User user) {
        DirContextAdapter context = lookup(user.getUsername());
        context.setAttributeValue("cn", user.getRealName());
        context.setAttributeValue("sn", user.getRealName());
        setOrRemoveAttribute(context, "mail", user.getEmail());
        setOrRemoveAttribute(context, "mobile", user.getMobile());
        setOrRemoveAttribute(context, "employeeNumber", user.getEmployeeNo());
        setOrRemoveAttribute(context, "departmentNumber", user.getDeptCode());
        ldapTemplate.modifyAttributes(context);
    }

    @Override
    public void enableUser(String username) {
        updateUserAttribute(username, "employeeType", "ENABLED");
    }

    @Override
    public void disableUser(String username) {
        updateUserAttribute(username, "employeeType", "DISABLED");
    }

    @Override
    public void deleteUser(String username) {
        DirContextAdapter context = lookup(username);
        ldapTemplate.unbind(context.getDn());
    }

    @Override
    public void resetPassword(String username, String rawPassword) {
        updateUserAttribute(username, "userPassword", rawPassword);
    }

    private void updateUserAttribute(String username, String attributeName, String value) {
        DirContextAdapter context = lookup(username);
        context.setAttributeValue(attributeName, value);
        ldapTemplate.modifyAttributes(context);
    }

    private DirContextAdapter lookup(String username) {
        try {
            return ldapTemplate.search(
                LdapQueryBuilder.query().base(ldapProperties.getPeopleOu()).where("uid").is(username),
                (ContextMapper<DirContextAdapter>) ctx -> (DirContextAdapter) ctx
            ).stream().findFirst().orElseThrow(() -> new BizException("LDAP_USER_NOT_FOUND", "LDAP 用户不存在"));
        } catch (NameNotFoundException exception) {
            throw new BizException("LDAP_USER_NOT_FOUND", "LDAP 用户不存在");
        }
    }

    private void putIfPresent(BasicAttributes attributes, String attributeName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        attributes.put(new BasicAttribute(attributeName, value));
    }

    private void setOrRemoveAttribute(DirContextAdapter context, String attributeName, String value) {
        if (value == null || value.isBlank()) {
            context.setAttributeValues(attributeName, new String[0]);
            return;
        }
        context.setAttributeValue(attributeName, value);
    }
}
