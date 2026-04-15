package com.company.idm.infrastructure.ldap;

import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.user.User;
import com.company.idm.infrastructure.config.AppLdapProperties;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ldap", name = "mode", havingValue = "stub", matchIfMissing = true)
public class StubLdapDirectoryService implements LdapDirectoryService {

    private final AppLdapProperties ldapProperties;
    private final Map<String, StubEntry> entries = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        ldapProperties.getStubUsers().forEach((username, password) ->
            entries.put(username, new StubEntry(password, true, buildDn(username))));
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
    public String createUser(User user, String rawPassword) {
        String dn = buildDn(user.getUsername());
        entries.put(user.getUsername(), new StubEntry(rawPassword, true, dn));
        return dn;
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
    public void resetPassword(String username, String rawPassword) {
        entries.computeIfPresent(username, (key, value) -> value.withPassword(rawPassword));
    }

    private String buildDn(String username) {
        return "uid=" + username + "," + ldapProperties.getPeopleOu() + "," + ldapProperties.getBaseDn();
    }

    private record StubEntry(String password, boolean enabled, String dn) {
        private StubEntry withEnabled(boolean newEnabled) {
            return new StubEntry(password, newEnabled, dn);
        }

        private StubEntry withPassword(String newPassword) {
            return new StubEntry(newPassword, enabled, dn);
        }
    }
}
