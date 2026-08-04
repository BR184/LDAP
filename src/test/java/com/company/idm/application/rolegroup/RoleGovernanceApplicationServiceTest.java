package com.company.idm.application.rolegroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.rolegroup.RoleGroup;
import com.company.idm.domain.rolegroup.RoleGroupRepository;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RoleGovernanceApplicationServiceTest {

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final RoleGroupRepository roleGroupRepository = mock(RoleGroupRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    private final PolicyRefreshService policyRefreshService = mock(PolicyRefreshService.class);
    private final RoleGroupAuthorizationService authorizationService = mock(RoleGroupAuthorizationService.class);
    private final RoleGovernanceApplicationService service = new RoleGovernanceApplicationService(
        roleRepository,
        roleGroupRepository,
        auditLogRepository,
        policyRefreshService,
        authorizationService
    );

    @Test
    void movesSystemRoleBackToTheExplicitTargetGroup() {
        Role systemRole = Role.builder()
            .id(8L)
            .roleCode("CAT_ADMIN")
            .roleName("CAT admin")
            .roleScope(RoleScope.SYSTEM)
            .status(1)
            .build();
        RoleGroup targetGroup = RoleGroup.builder().id(21L).groupName("CAT").status(1).build();
        when(authorizationService.isPlatformAdmin(principal())).thenReturn(true);
        when(roleRepository.findById(8L)).thenReturn(Optional.of(systemRole));
        when(roleGroupRepository.findById(21L)).thenReturn(Optional.of(targetGroup));
        when(roleRepository.findPermissionIdsByRoleId(8L)).thenReturn(List.of());
        when(roleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Role updated = service.updateScope(8L, RoleScope.GROUP, 21L, principal());

        assertThat(updated.getRoleScope()).isEqualTo(RoleScope.GROUP);
        assertThat(updated.getRoleGroupId()).isEqualTo(21L);
        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(roleCaptor.capture());
        assertThat(roleCaptor.getValue().getRoleGroupId()).isEqualTo(21L);
    }

    private AuthenticatedUser principal() {
        return new AuthenticatedUser(
            1L,
            "admin",
            1,
            Set.of("ADMIN"),
            CredentialType.SESSION,
            null,
            Set.of(),
            null,
            PersonalAccessTokenSubjectType.USER,
            1L
        );
    }
}
