package com.company.idm.application.rolegroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleSupplyScopePolicyTest {

    private final RoleSupplyScopePolicy policy = new RoleSupplyScopePolicy();

    @Test
    void groupTokensSeeOnlyTheirGroupAndGlobalRoles() {
        List<Role> visible = policy.filterVisibleRoles(
            PersonalAccessTokenSubjectType.ROLE_GROUP,
            10L,
            roles()
        );

        assertThat(visible).extracting(Role::getRoleCode)
            .containsExactly("GLOBAL_USER", "GROUP_A");
    }

    @Test
    void globalTokensSeeGlobalGroupAndSystemRoles() {
        List<Role> visible = policy.filterVisibleRoles(
            PersonalAccessTokenSubjectType.GLOBAL,
            null,
            roles()
        );

        assertThat(visible).extracting(Role::getRoleCode)
            .containsExactly("GLOBAL_USER", "GROUP_A", "GROUP_B", "SYSTEM_ADMIN");
    }

    private List<Role> roles() {
        return List.of(
            role("GLOBAL_USER", RoleScope.GLOBAL, null),
            role("GROUP_A", RoleScope.GROUP, 10L),
            role("GROUP_B", RoleScope.GROUP, 20L),
            role("SYSTEM_ADMIN", RoleScope.SYSTEM, null)
        );
    }

    private Role role(String code, RoleScope scope, Long groupId) {
        return Role.builder()
            .id((long) code.hashCode())
            .roleCode(code)
            .roleName(code)
            .roleScope(scope)
            .roleGroupId(groupId)
            .status(1)
            .build();
    }
}
