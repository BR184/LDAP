package com.company.idm.application.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EffectivePermissionServiceTest {

    private final PermissionRepository permissionRepository = mock(PermissionRepository.class);
    private final EffectivePermissionService service = new EffectivePermissionService(permissionRepository);

    @Test
    void sessionUsesAllCurrentAccountPermissions() {
        when(permissionRepository.findPermissionCodesByUserId("employee"))
            .thenReturn(Set.of("AUTH_ME", "USER_READ"));
        AuthenticatedUser principal = new AuthenticatedUser(
            1L,
            "employee",
            0,
            Set.of("NORMAL_USER"),
            CredentialType.SESSION,
            null,
            Set.of()
        );

        assertThat(service.resolve(principal)).containsExactlyInAnyOrder("AUTH_ME", "USER_READ");
    }

    @Test
    void personalAccessTokenUsesOnlySelectedCurrentPermissions() {
        when(permissionRepository.findPermissionCodesByUserId("employee"))
            .thenReturn(Set.of("AUTH_ME", "USER_READ", "USER_CREATE"));
        AuthenticatedUser principal = new AuthenticatedUser(
            1L,
            "employee",
            0,
            Set.of("NORMAL_USER"),
            CredentialType.PERSONAL_ACCESS_TOKEN,
            99L,
            Set.of("AUTH_ME", "USER_READ")
        );

        assertThat(service.resolve(principal)).containsExactlyInAnyOrder("AUTH_ME", "USER_READ");
    }

    @Test
    void personalAccessTokenDoesNotGainPermissionsAddedToAccountLater() {
        when(permissionRepository.findPermissionCodesByUserId("employee"))
            .thenReturn(Set.of("AUTH_ME", "USER_READ", "USER_CREATE", "ROLE_READ"));
        AuthenticatedUser principal = new AuthenticatedUser(
            1L,
            "employee",
            0,
            Set.of("NORMAL_USER"),
            CredentialType.PERSONAL_ACCESS_TOKEN,
            99L,
            Set.of("AUTH_ME")
        );

        assertThat(service.resolve(principal)).containsExactly("AUTH_ME");
    }
}
