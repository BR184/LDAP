package com.company.idm.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PersonalAccessTokenRequestPolicyTest {

    private final PersonalAccessTokenRequestPolicy policy = new PersonalAccessTokenRequestPolicy();

    @Test
    void roleSupplyTokensCanOnlyEnterTheOpenSupplyApi() {
        AuthenticatedUser groupToken = tokenPrincipal(PersonalAccessTokenSubjectType.ROLE_GROUP, 9L);

        assertThat(policy.allows(groupToken, "/api/v1/open/role-supply/snapshot")).isTrue();
        assertThat(policy.allows(groupToken, "/api/v1/open/role-supply/changes")).isTrue();
        assertThat(policy.allows(groupToken, "/api/v1/role-groups/9")).isFalse();
        assertThat(policy.allows(groupToken, "/api/v1/users")).isFalse();
        assertThat(policy.allows(groupToken, "/api/v1/open/role-supply-admin")).isFalse();
    }

    @Test
    void userTokensKeepTheirExistingApiPolicy() {
        AuthenticatedUser userToken = tokenPrincipal(PersonalAccessTokenSubjectType.USER, 7L);

        assertThat(policy.allows(userToken, "/api/v1/users")).isTrue();
        assertThat(policy.allows(userToken, "/api/v1/personal-access-tokens")).isFalse();
    }

    private AuthenticatedUser tokenPrincipal(PersonalAccessTokenSubjectType subjectType, Long subjectId) {
        return new AuthenticatedUser(
            subjectType == PersonalAccessTokenSubjectType.USER ? subjectId : null,
            "token-subject",
            0,
            Set.of(),
            CredentialType.PERSONAL_ACCESS_TOKEN,
            11L,
            Set.of(),
            null,
            subjectType,
            subjectId
        );
    }
}
