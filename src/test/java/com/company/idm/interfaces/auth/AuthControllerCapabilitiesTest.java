package com.company.idm.interfaces.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.idm.application.auth.AuthApplicationService;
import com.company.idm.application.rbac.EffectivePermissionService;
import com.company.idm.application.user.PasswordResetApplicationService;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AuthControllerCapabilitiesTest {

    private final AuthApplicationService authApplicationService = mock(AuthApplicationService.class);
    private final PasswordResetApplicationService passwordResetApplicationService =
        mock(PasswordResetApplicationService.class);
    private final EffectivePermissionService effectivePermissionService = mock(EffectivePermissionService.class);
    private final AuthController controller = new AuthController(
        authApplicationService,
        passwordResetApplicationService,
        effectivePermissionService
    );

    @Test
    void returnsDeterministicEffectivePermissionCodesForCurrentSession() {
        AuthenticatedUser principal = session("employee");
        when(effectivePermissionService.resolve(principal))
            .thenReturn(Set.of("USER_READ", "AUTH_ME", "MENU_VIEW_USER_MANAGEMENT"));

        ApiResponse<CurrentCapabilitiesResponse> response = controller.capabilities(principal);

        assertThat(response.getData().permissionCodes())
            .containsExactly("AUTH_ME", "MENU_VIEW_USER_MANAGEMENT", "USER_READ");
    }

    @Test
    void endpointOnlyAcceptsInteractiveSessions() throws Exception {
        Method method = AuthController.class.getDeclaredMethod("capabilities", AuthenticatedUser.class);

        assertThat(method.getAnnotation(PreAuthorize.class))
            .isNotNull()
            .extracting(PreAuthorize::value)
            .isEqualTo("@credentialAccessService.isSession(authentication)");
    }

    private AuthenticatedUser session(String userId) {
        return new AuthenticatedUser(
            1L,
            userId,
            0,
            Set.of("NORMAL_USER"),
            CredentialType.SESSION,
            null,
            Set.of(),
            null,
            PersonalAccessTokenSubjectType.USER,
            1L
        );
    }
}
