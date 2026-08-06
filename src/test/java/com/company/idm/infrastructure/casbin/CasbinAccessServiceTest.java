package com.company.idm.infrastructure.casbin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.rbac.EffectivePermissionService;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import com.company.idm.domain.token.PersonalAccessTokenScopeMode;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class CasbinAccessServiceTest {

    private final CasbinPolicyService policyService = mock(CasbinPolicyService.class);
    private final EffectivePermissionService effectivePermissionService = mock(EffectivePermissionService.class);
    private final CasbinAccessService service = new CasbinAccessService(policyService, effectivePermissionService);

    @Test
    void grantsOnlyEffectivePermissionBackedByCurrentRolePolicy() {
        AuthenticatedUser principal = principal();
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, Set.of());
        when(effectivePermissionService.resolve(principal)).thenReturn(Set.of("USER_READ"));
        when(policyService.enforce("employee", "USER_READ", "GRANT")).thenReturn(true);

        assertThat(service.hasAny(authentication, "USER_CREATE", "USER_READ")).isTrue();
    }

    @Test
    void deniesPermissionOutsideCredentialScope() {
        AuthenticatedUser principal = principal();
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, Set.of());
        when(effectivePermissionService.resolve(principal)).thenReturn(Set.of("AUTH_ME"));
        when(policyService.enforce("employee", "USER_READ", "GRANT")).thenReturn(true);

        assertThat(service.hasAny(authentication, "USER_READ")).isFalse();
    }

    @Test
    void refreshesLocalPoliciesWhenTheDatabaseShowsANewGrant() {
        AuthenticatedUser principal = principal();
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, Set.of());
        when(effectivePermissionService.resolve(principal)).thenReturn(Set.of("USER_READ"));
        when(policyService.enforce("employee", "USER_READ", "GRANT"))
            .thenReturn(false, true);

        assertThat(service.hasAny(authentication, "USER_READ")).isTrue();

        verify(policyService).refresh();
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
            1L,
            "employee",
            0,
            Set.of("NORMAL_USER"),
            CredentialType.PERSONAL_ACCESS_TOKEN,
            99L,
            Set.of("AUTH_ME", "USER_READ"),
            PersonalAccessTokenScopeMode.FIXED,
            PersonalAccessTokenSubjectType.USER,
            1L
        );
    }
}
