package com.company.idm.application.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.user.User;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserReadScopeServiceTest {

    private final UserReadScopeService service = new UserReadScopeService(new ReportingHierarchyService());

    @Test
    void selfAndSubordinateTreePermissionReturnsOperatorAndRecursiveDescendants() {
        User manager = user(1L, "manager", "E001", null);
        User directSubordinate = user(2L, "direct", "E002", "manager");
        User recursiveSubordinate = user(3L, "recursive", "E003", "direct");
        User unrelatedUser = user(4L, "unrelated", "E004", null);
        List<User> organization = List.of(manager, directSubordinate, recursiveSubordinate, unrelatedUser);

        List<User> visibleUsers = service.filterVisibleUsers(
            manager,
            organization,
            organization,
            Set.of(UserReadScopeService.USER_READ_SELF_AND_SUBORDINATE_TREE)
        );

        assertThat(visibleUsers).extracting(User::getId).containsExactly(1L, 2L, 3L);
        service.checkCanReadUser(
            manager,
            manager,
            organization,
            Set.of(UserReadScopeService.USER_READ_SELF_AND_SUBORDINATE_TREE)
        );
        service.checkCanReadUser(
            manager,
            recursiveSubordinate,
            organization,
            Set.of(UserReadScopeService.USER_READ_SELF_AND_SUBORDINATE_TREE)
        );
        assertThatThrownBy(() -> service.checkCanReadUser(
            manager,
            unrelatedUser,
            organization,
            Set.of(UserReadScopeService.USER_READ_SELF_AND_SUBORDINATE_TREE)
        )).isInstanceOf(BizException.class);
    }

    @Test
    void globalUserReadTakesPrecedenceOverSubordinateTreeScope() {
        User manager = user(1L, "manager", "E001", null);
        User unrelatedUser = user(2L, "unrelated", "E002", null);
        List<User> organization = List.of(manager, unrelatedUser);

        List<User> visibleUsers = service.filterVisibleUsers(
            manager,
            organization,
            organization,
            Set.of(UserReadScopeService.USER_READ, UserReadScopeService.USER_READ_SELF_AND_SUBORDINATE_TREE)
        );

        assertThat(visibleUsers).containsExactly(manager, unrelatedUser);
    }

    @Test
    void subordinateTreeExcludesOperatorWhenReportingDataContainsCycle() {
        User manager = user(1L, "manager", "E001", "recursive");
        User directSubordinate = user(2L, "direct", "E002", "manager");
        User recursiveSubordinate = user(3L, "recursive", "E003", "direct");
        List<User> organization = List.of(manager, directSubordinate, recursiveSubordinate);

        List<User> visibleUsers = service.filterVisibleUsers(
            manager,
            organization,
            organization,
            Set.of(UserReadScopeService.USER_READ_SELF_AND_SUBORDINATE_TREE)
        );

        assertThat(visibleUsers).extracting(User::getId).containsExactly(1L, 2L, 3L);
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
