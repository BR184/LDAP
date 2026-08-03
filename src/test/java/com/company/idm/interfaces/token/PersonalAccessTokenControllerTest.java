package com.company.idm.interfaces.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.company.idm.application.token.CreatePersonalAccessTokenCommand;
import com.company.idm.application.token.CreatedPersonalAccessToken;
import com.company.idm.application.token.PersonalAccessTokenApplicationService;
import com.company.idm.application.token.PersonalAccessTokenPage;
import com.company.idm.domain.token.PersonalAccessToken;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PersonalAccessTokenControllerTest {

    private final PersonalAccessTokenApplicationService applicationService =
        mock(PersonalAccessTokenApplicationService.class);
    private final PersonalAccessTokenController controller = new PersonalAccessTokenController(applicationService);

    @Test
    void listReturnsMetadataWithoutSecretMaterial() {
        PersonalAccessToken token = token();
        when(applicationService.list(principal(), 1, 20)).thenReturn(new PersonalAccessTokenPage(
            List.of(token),
            1,
            1,
            20
        ));

        PersonalAccessTokenResponse response = controller.list(1, 20, principal())
            .getData()
            .items()
            .get(0);

        assertThat(response.tokenPrefix()).isEqualTo("idm_pat_uid_...");
        assertThat(response.toString()).doesNotContain("stored-hash", "idm_pat_uid_secret");
    }

    @Test
    void createIsTheOnlyResponseThatContainsTheFullSecret() {
        CreatePersonalAccessTokenRequest request = new CreatePersonalAccessTokenRequest(
            "automation",
            null,
            List.of(1L),
            "verification"
        );
        when(applicationService.create(
            org.mockito.ArgumentMatchers.eq(principal()),
            org.mockito.ArgumentMatchers.any(CreatePersonalAccessTokenCommand.class)
        )).thenReturn(new CreatedPersonalAccessToken(token(), "idm_pat_uid_secret"));

        CreatedPersonalAccessTokenResponse response = controller.create(
            request,
            principal(),
            new MockHttpServletRequest()
        ).getData();

        assertThat(response.secret()).isEqualTo("idm_pat_uid_secret");
        assertThat(response.token().toString()).doesNotContain("stored-hash");
    }

    private PersonalAccessToken token() {
        return PersonalAccessToken.builder()
            .id(11L)
            .tokenUid("uid")
            .userId(7L)
            .name("automation")
            .secretHash("stored-hash")
            .hashVersion(1)
            .tokenPrefix("idm_pat_uid_...")
            .gmtCreate(LocalDateTime.now())
            .permissions(List.of())
            .build();
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(7L, "employee", 0, Set.of(), CredentialType.SESSION, null, Set.of());
    }
}
