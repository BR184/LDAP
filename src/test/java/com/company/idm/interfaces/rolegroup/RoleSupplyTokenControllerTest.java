package com.company.idm.interfaces.rolegroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.idm.application.token.CreatedPersonalAccessToken;
import com.company.idm.application.token.RoleSupplyTokenApplicationService;
import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RoleSupplyTokenControllerTest {

    @Test
    void responsesContainingASecretCannotBeCached() {
        RoleSupplyTokenApplicationService applicationService = mock(RoleSupplyTokenApplicationService.class);
        RoleSupplyTokenController controller = new RoleSupplyTokenController(applicationService);
        PersonalAccessToken token = PersonalAccessToken.builder()
            .id(80L)
            .subjectType(PersonalAccessTokenSubjectType.ROLE_GROUP)
            .subjectId(10L)
            .name("财务系统同步")
            .build();
        when(applicationService.createGroupToken(
            eq(10L), any(), eq("财务系统同步"), eq(null), eq(null), any()
        )).thenReturn(new CreatedPersonalAccessToken(token, "idm_pat_uid_secret"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.createGroupToken(
            10L,
            new RoleSupplyTokenController.SaveRoleSupplyTokenRequest("财务系统同步", null, null),
            session(),
            new MockHttpServletRequest(),
            response
        );

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getDateHeader("Expires")).isZero();
    }

    private AuthenticatedUser session() {
        return new AuthenticatedUser(
            7L,
            "delegate",
            0,
            Set.of("NORMAL_USER"),
            CredentialType.SESSION,
            null,
            Set.of(),
            null,
            PersonalAccessTokenSubjectType.USER,
            7L
        );
    }
}
