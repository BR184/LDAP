package com.company.idm.infrastructure.ldap;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapUserSnapshot;
import com.company.idm.domain.user.User;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ldap", name = "mode", havingValue = "spring")
public class SpringLdapDirectoryService implements LdapDirectoryService {

    private final LdapTemplate ldapTemplate;
    private final AppLdapProperties ldapProperties;

    @Override
    public boolean authenticate(String userId, String password) {
        try {
            ldapTemplate.authenticate(
                LdapQueryBuilder.query().base(ldapProperties.getPeopleOu()).where("uid").is(userId),
                password
            );
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean existsByUid(String userId) {
        return !ldapTemplate.search(
            LdapQueryBuilder.query().base(ldapProperties.getPeopleOu()).where("uid").is(userId),
            (AttributesMapper<String>) attributes -> attributes.get("uid") == null ? null : attributes.get("uid").get().toString()
        ).isEmpty();
    }

    @Override
    public LdapUserSnapshot findUserSnapshot(String userId) {
        DirContextAdapter context = lookup(userId);
        return LdapUserSnapshot.builder()
            .userId(userId)
            .realName(context.getStringAttribute("cn"))
            .email(context.getStringAttribute("mail"))
            .mobile(context.getStringAttribute("mobile"))
            .employeeNo(context.getStringAttribute("employeeNumber"))
            .deptCode(context.getStringAttribute("departmentNumber"))
            .status(context.getStringAttribute("employeeType"))
            .dn(LdapDnHelper.toAbsoluteDn(ldapProperties, context.getDn()))
            .build();
    }

    @Override
    public List<String> listAllUserIds() {
        return ldapTemplate.search(
            LdapQueryBuilder.query().base(ldapProperties.getPeopleOu()).where("objectClass").is("inetOrgPerson"),
            (AttributesMapper<String>) attributes -> attributes.get("uid") == null ? null : attributes.get("uid").get().toString()
        ).stream()
            .filter(userId -> userId != null && !"placeholder".equals(userId))
            .sorted()
            .toList();
    }

    @Override
    public String createUser(User user, String rawPassword) {
        Name dn = LdapDnHelper.buildRelativeUserDn(ldapProperties, user.getUserId());
        BasicAttributes attributes = new BasicAttributes();
        attributes.put("objectClass", "inetOrgPerson");
        attributes.put(new BasicAttribute("uid", user.getUserId()));
        attributes.put(new BasicAttribute("cn", user.getRealName()));
        attributes.put(new BasicAttribute("sn", user.getRealName()));
        putIfPresent(attributes, "mail", resolveMail(user));
        putIfPresent(attributes, "mobile", user.getMobile());
        putIfPresent(attributes, "employeeNumber", user.getEmployeeNo());
        putIfPresent(attributes, "departmentNumber", user.getDeptCode());
        attributes.put(new BasicAttribute("employeeType", "ENABLED"));
        attributes.put(new BasicAttribute("userPassword", rawPassword));
        ldapTemplate.bind(dn, null, attributes);
        return LdapDnHelper.toAbsoluteDn(ldapProperties, dn);
    }

    @Override
    public void updateUser(User user) {
        DirContextAdapter context = lookup(user.getUserId());
        context.setAttributeValue("cn", user.getRealName());
        context.setAttributeValue("sn", user.getRealName());
        setOrRemoveAttribute(context, "mail", resolveMail(user));
        setOrRemoveAttribute(context, "mobile", user.getMobile());
        setOrRemoveAttribute(context, "employeeNumber", user.getEmployeeNo());
        setOrRemoveAttribute(context, "departmentNumber", user.getDeptCode());
        ldapTemplate.modifyAttributes(context);
    }

    @Override
    public void enableUser(String userId) {
        updateUserAttribute(userId, "employeeType", "ENABLED");
    }

    @Override
    public void disableUser(String userId) {
        updateUserAttribute(userId, "employeeType", "DISABLED");
    }

    @Override
    public void deleteUser(String userId) {
        DirContextAdapter context = lookup(userId);
        ldapTemplate.unbind(context.getDn());
    }

    @Override
    public void resetPassword(String userId, String rawPassword) {
        updateUserAttribute(userId, "userPassword", rawPassword);
    }

    private void updateUserAttribute(String userId, String attributeName, String value) {
        DirContextAdapter context = lookup(userId);
        context.setAttributeValue(attributeName, value);
        ldapTemplate.modifyAttributes(context);
    }

    private DirContextAdapter lookup(String userId) {
        try {
            return ldapTemplate.search(
                LdapQueryBuilder.query().base(ldapProperties.getPeopleOu()).where("uid").is(userId),
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

    private String resolveMail(User user) {
        if (user == null) {
            return null;
        }
        if (user.getIntranetEmail() != null && !user.getIntranetEmail().isBlank()) {
            return user.getIntranetEmail();
        }
        return user.getEmail();
    }
}
