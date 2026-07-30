package com.company.idm.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.idm.common.enums.EmploymentStatus;
import org.junit.jupiter.api.Test;

class UserAccessPolicyTest {

    private final UserAccessPolicy policy = new UserAccessPolicy();

    @Test
    void allowsActiveUserWhenAdministratorAllowsAccess() {
        User user = user(true, EmploymentStatus.ACTIVE);

        assertThat(policy.canAuthenticate(user)).isTrue();
    }

    @Test
    void rejectsActiveUserWhenAdministratorDeniesAccess() {
        User user = user(false, EmploymentStatus.ACTIVE);

        assertThat(policy.canAuthenticate(user)).isFalse();
    }

    @Test
    void rejectsResignedUserEvenWhenAdministratorAllowsAccess() {
        User user = user(true, EmploymentStatus.RESIGNED);

        assertThat(policy.canAuthenticate(user)).isFalse();
    }

    @Test
    void keepsAdministratorDenialEffectiveAfterUserReturnsToActiveEmployment() {
        User resigned = user(false, EmploymentStatus.RESIGNED);
        User activeAgain = resigned.toBuilder()
            .employmentStatus(EmploymentStatus.ACTIVE)
            .build();

        assertThat(policy.canAuthenticate(activeAgain)).isFalse();
    }

    private User user(boolean accessAllowed, EmploymentStatus employmentStatus) {
        return User.builder()
            .userId("zhangsan")
            .accessAllowed(accessAllowed)
            .employmentStatus(employmentStatus)
            .build();
    }
}
