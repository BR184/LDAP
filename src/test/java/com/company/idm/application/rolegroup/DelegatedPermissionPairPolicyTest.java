package com.company.idm.application.rolegroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.idm.common.exception.BizException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DelegatedPermissionPairPolicyTest {

    private final DelegatedPermissionPairPolicy policy = new DelegatedPermissionPairPolicy();

    @Test
    void acceptsTheCompleteDelegationPair() {
        assertThat(policy.canManage(Set.of(
            DelegatedPermissionPairPolicy.ROLE_GROUP_READ,
            DelegatedPermissionPairPolicy.ROLE_GROUP_MANAGE,
            DelegatedPermissionPairPolicy.ROLE_GROUP_USER_ASSIGN
        ))).isTrue();
    }

    @Test
    void acceptsReadOnlyRoleGroupAccess() {
        assertThat(policy.canRead(Set.of(
            DelegatedPermissionPairPolicy.ROLE_GROUP_READ,
            DelegatedPermissionPairPolicy.ROLE_GROUP_MENU_VIEW
        ))).isTrue();
    }

    @Test
    void preservesReadAccessForExistingManagementCredentials() {
        assertThat(policy.canRead(Set.of(
            DelegatedPermissionPairPolicy.ROLE_GROUP_MANAGE,
            DelegatedPermissionPairPolicy.ROLE_GROUP_USER_ASSIGN
        ))).isTrue();
    }

    @Test
    void rejectsEitherDelegationPermissionOnItsOwn() {
        assertThatThrownBy(() -> policy.validate(Set.of(
            DelegatedPermissionPairPolicy.ROLE_GROUP_MANAGE
        )))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("同时授予");

        assertThatThrownBy(() -> policy.validate(Set.of(
            DelegatedPermissionPairPolicy.ROLE_GROUP_USER_ASSIGN
        )))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("同时授予");
    }

    @Test
    void treatsAnAbsentPairAsNoDelegation() {
        assertThat(policy.canRead(Set.of("ROLE_READ"))).isFalse();
        assertThat(policy.canManage(Set.of("ROLE_READ"))).isFalse();
    }

}
