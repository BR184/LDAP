package com.company.idm.infrastructure.ldap;

import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapUserSnapshot;
import com.company.idm.domain.user.User;
import com.company.idm.infrastructure.config.AppLdapProperties;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 提供用于本地原型联调的 LDAP 桩实现。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ldap", name = "mode", havingValue = "stub", matchIfMissing = true)
public class StubLdapDirectoryService implements LdapDirectoryService {

    private final AppLdapProperties ldapProperties;
    private final Map<String, StubEntry> entries = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        ldapProperties.getStubUsers().forEach((username, password) ->
            entries.put(username, new StubEntry(password, true, buildDn(username), username, null, null, null, null)));
    }

    @Override
    public boolean authenticate(String username, String password) {
        StubEntry entry = entries.get(username);
        return entry != null && entry.enabled && entry.password.equals(password);
    }

    @Override
    public boolean existsByUid(String username) {
        return entries.containsKey(username);
    }

    @Override
    public LdapUserSnapshot findUserSnapshot(String username) {
        StubEntry entry = entries.get(username);
        if (entry == null) {
            return null;
        }
        return LdapUserSnapshot.builder()
            .username(username)
            .realName(entry.realName)
            .email(entry.email)
            .mobile(entry.mobile)
            .employeeNo(entry.employeeNo)
            .deptCode(entry.deptCode)
            .status(entry.enabled ? "ENABLED" : "DISABLED")
            .dn(entry.dn)
            .build();
    }

    @Override
    public List<String> listAllUsernames() {
        return entries.keySet().stream()
            .filter(username -> !"placeholder".equals(username))
            .sorted()
            .toList();
    }

    @Override
    public String createUser(User user, String rawPassword) {
        String dn = buildDn(user.getUsername());
        entries.put(user.getUsername(), new StubEntry(
            rawPassword,
            true,
            dn,
            user.getRealName(),
            user.getEmail(),
            user.getMobile(),
            user.getEmployeeNo(),
            user.getDeptCode()
        ));
        return dn;
    }

    @Override
    public void updateUser(User user) {
        entries.computeIfPresent(user.getUsername(), (key, value) -> value.withProfile(
            user.getRealName(),
            user.getEmail(),
            user.getMobile(),
            user.getEmployeeNo(),
            user.getDeptCode()
        ));
    }

    @Override
    public void enableUser(String username) {
        entries.computeIfPresent(username, (key, value) -> value.withEnabled(true));
    }

    @Override
    public void disableUser(String username) {
        entries.computeIfPresent(username, (key, value) -> value.withEnabled(false));
    }

    @Override
    public void deleteUser(String username) {
        entries.remove(username);
    }

    @Override
    public void resetPassword(String username, String rawPassword) {
        entries.computeIfPresent(username, (key, value) -> value.withPassword(rawPassword));
    }

    private String buildDn(String username) {
        return LdapDnHelper.buildUserDn(ldapProperties, username);
    }

    private record StubEntry(
        String password,
        boolean enabled,
        String dn,
        String realName,
        String email,
        String mobile,
        String employeeNo,
        String deptCode
    ) {
        private StubEntry withEnabled(boolean newEnabled) {
            return new StubEntry(password, newEnabled, dn, realName, email, mobile, employeeNo, deptCode);
        }

        private StubEntry withPassword(String newPassword) {
            return new StubEntry(newPassword, enabled, dn, realName, email, mobile, employeeNo, deptCode);
        }

        private StubEntry withProfile(String newRealName, String newEmail, String newMobile, String newEmployeeNo, String newDeptCode) {
            return new StubEntry(password, enabled, dn, newRealName, newEmail, newMobile, newEmployeeNo, newDeptCode);
        }
    }
}
