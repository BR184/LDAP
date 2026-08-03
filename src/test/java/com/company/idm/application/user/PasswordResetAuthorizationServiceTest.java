package com.company.idm.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import com.company.idm.domain.user.User;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PasswordResetAuthorizationServiceTest {

    private final PasswordResetAuthorizationService service = new PasswordResetAuthorizationService(
        new ReportingHierarchyService()
    );

    @Test
    void directResetPermissionUsesLeaderUserIdRatherThanEmployeeNo() {
        User manager = user(1L, "manager", "E001", null);
        User directSubordinate = user(2L, "direct", "E002", "manager");

        PasswordResetAuthorizationResult result = service.evaluate(
            manager,
            directSubordinate,
            List.of(manager, directSubordinate),
            Set.of(PasswordResetAuthorizationService.RESET_DIRECT)
        );

        assertThat(result.allowed()).isTrue();
        assertThat(result.scope()).isEqualTo(PasswordResetScope.DIRECT);
    }

    @Test
    void userCannotResetOwnPasswordEvenWhenTheyCanReadTheirOwnUserRecord() {
        User manager = user(1L, "manager", "E001", null);

        PasswordResetAuthorizationResult result = service.evaluate(
            manager,
            manager,
            List.of(manager),
            Set.of(
                PasswordResetAuthorizationService.RESET_DIRECT,
                PasswordResetAuthorizationService.RESET_TREE
            )
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.scope()).isNull();
    }

    @Test
    void roleNameCannotExpandTheCredentialPermissionScope() {
        User manager = user(1L, "manager", "E001", null).toBuilder()
            .roleCodes(Set.of("SUPER_ADMIN"))
            .build();
        User unrelatedUser = user(2L, "unrelated", "E002", null);

        PasswordResetAuthorizationResult result = service.evaluate(
            manager,
            unrelatedUser,
            List.of(manager, unrelatedUser),
            Set.of(PasswordResetAuthorizationService.RESET_DIRECT)
        );

        assertThat(result.allowed()).isFalse();
    }

    private User user(Long id, String userId, String employeeNo, String leaderRef) {
        return User.builder()
            .id(id)
            .userId(userId)
            .employeeNo(employeeNo)
            .leaderRef(leaderRef)
            .build();
    }
}
