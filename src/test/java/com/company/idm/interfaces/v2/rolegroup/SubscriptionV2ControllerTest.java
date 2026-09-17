package com.company.idm.interfaces.v2.rolegroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.rolegroup.CreatedSubscription;
import com.company.idm.application.rolegroup.SubscriptionApplicationService;
import com.company.idm.application.rolegroup.SubscriptionCredential;
import com.company.idm.application.rolegroup.SubscriptionMqInfo;
import com.company.idm.application.rolegroup.SubscriptionView;
import com.company.idm.domain.rolegroup.PushSubscriptionStatus;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SubscriptionV2ControllerTest {

    private final SubscriptionApplicationService applicationService = mock(SubscriptionApplicationService.class);
    private final SubscriptionV2Controller controller = new SubscriptionV2Controller(applicationService);

    @Test
    void responsesContainingASecretCannotBeCached() {
        when(applicationService.createGroupSubscription(
            any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(createdSubscription());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("idm.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.createGroupSubscription(
            10L,
            new SubscriptionV2Controller.SaveSubscriptionRequest("财务系统同步", null, List.of(11L)),
            session(),
            request,
            response
        );

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getDateHeader("Expires")).isZero();
    }

    @Test
    void creationDelegatesToApplicationServiceWithClientContext() {
        when(applicationService.createGroupSubscription(
            any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(createdSubscription());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServerName("idm.example.com");

        var result = controller.createGroupSubscription(
            10L,
            new SubscriptionV2Controller.SaveSubscriptionRequest("财务系统同步", null, List.of(11L)),
            session(),
            request,
            new MockHttpServletResponse()
        );

        verify(applicationService).createGroupSubscription(
            10L, "财务系统同步", null, List.of(11L), session(), "127.0.0.1", "idm.example.com"
        );
        assertThat(result.data().credential().tokenSecret()).isEqualTo("idm_pat_secret");
        assertThat(result.data().credential().mq().queue()).isEqualTo("idm.sub.3");
        assertThat(result.data().subscription().status()).isEqualTo("ENABLED");
    }

    @Test
    void rotationResponsesAlsoDisableSecretCaching() {
        when(applicationService.rotateSubscription(any(), any(), any(), any())).thenReturn(credential());
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.rotateGroupSubscription(10L, 3L, session(), new MockHttpServletRequest(), response);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("Pragma")).isEqualTo("no-cache");
        assertThat(response.getDateHeader("Expires")).isZero();
    }

    private CreatedSubscription createdSubscription() {
        SubscriptionView view = new SubscriptionView(
            3L,
            "财务系统同步",
            null,
            PersonalAccessTokenSubjectType.ROLE_GROUP,
            10L,
            PushSubscriptionStatus.ENABLED,
            1,
            "delegate",
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        return new CreatedSubscription(view, credential());
    }

    private SubscriptionCredential credential() {
        return new SubscriptionCredential(
            3L,
            "idm_pat_secret",
            new SubscriptionMqInfo("idm.example.com", 5672, "/", "idm.sub.3", "idm-sub-3", "mq-password")
        );
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
