package com.company.idm.application.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.user.User;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemAdministratorProtectionPolicyTest {

    private final SystemAdministratorProtectionPolicy policy = new SystemAdministratorProtectionPolicy();
    private final User admin = User.builder().id(1L).userId("admin").build();

    @Test
    void protectsAdminIndependentlyOfCurrentRoleBindings() {
        assertCode(() -> policy.checkProfileMutable(admin), "SYSTEM_ADMIN_PROFILE_LOCKED");
        assertCode(() -> policy.checkAccessChangeAllowed(admin, false), "SYSTEM_ADMIN_ACCESS_REQUIRED");
        assertCode(() -> policy.checkDeletable(admin), "USER_DELETE_SYSTEM_ADMIN_FORBIDDEN");
        assertCode(() -> policy.checkImportTargetAllowed("ADMIN"), "SYSTEM_ADMIN_IMPORT_FORBIDDEN");
        assertCode(() -> policy.checkUserIdAvailableForCreation(" admin "), "SYSTEM_ADMIN_USER_ID_RESERVED");
    }

    @Test
    void requiresTheSuperAdminRoleButAllowsAdditionalRoles() {
        Role normalRole = Role.builder().id(2L).roleCode("NORMAL_USER").build();
        Role superAdminRole = Role.builder().id(1L).roleCode("SUPER_ADMIN").build();

        assertCode(
            () -> policy.checkRetainsSuperAdministratorRole(admin, List.of(normalRole)),
            "SYSTEM_ADMIN_SUPER_ADMIN_REQUIRED"
        );
        policy.checkRetainsSuperAdministratorRole(admin, List.of(superAdminRole, normalRole));
    }

    private void assertCode(Runnable operation, String expectedCode) {
        assertThatThrownBy(operation::run)
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo(expectedCode);
    }
}
