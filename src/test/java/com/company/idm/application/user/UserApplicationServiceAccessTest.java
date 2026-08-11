package com.company.idm.application.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.user.PasswordPolicyValidator;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserAccessPolicy;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.AppLdapProperties;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserApplicationServiceAccessTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LdapDirectoryService ldapDirectoryService = mock(LdapDirectoryService.class);
    private final PermissionLevelRuleService permissionLevelRuleService = mock(PermissionLevelRuleService.class);
    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final UserApplicationService service = new UserApplicationService(
        userRepository,
        mock(DepartmentRepository.class),
        ldapDirectoryService,
        mock(LdapGroupService.class),
        mock(AuditLogRepository.class),
        mock(PolicyRefreshService.class),
        mock(PasswordPolicyValidator.class),
        permissionLevelRuleService,
        roleRepository,
        mock(IntranetEmailGenerationService.class),
        mock(AppLdapProperties.class),
        mock(PasswordVerificationTokenService.class),
        mock(InitialPasswordPolicy.class),
        new UserAccessPolicy(),
        new SystemAdministratorProtectionPolicy(),
        mock(UserReadScopeService.class)
    );

    @Test
    void administratorDenialRevokesTokensAndDisablesLdap() {
        User user = user(true, EmploymentStatus.ACTIVE, 3);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        service.updateAccess(new UpdateUserAccessCommand(7L, false, "admin"));

        verify(permissionLevelRuleService).checkCanModifySensitiveUser("admin", user);
        verify(userRepository).updateAccessAllowed(7L, false, 4, "admin");
        verify(ldapDirectoryService).disableUser("zhangsan");
    }

    @Test
    void administratorApprovalDoesNotEnableLdapForResignedEmployee() {
        User user = user(false, EmploymentStatus.RESIGNED, 8);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        service.updateAccess(new UpdateUserAccessCommand(7L, true, "admin"));

        verify(userRepository).updateAccessAllowed(7L, true, 9, "admin");
        verify(ldapDirectoryService).disableUser("zhangsan");
    }

    @Test
    void builtInAdminCannotBeDisabledEvenIfItsRoleBindingWasLost() {
        User admin = User.builder()
            .id(1L)
            .userId("admin")
            .realName("系统管理员")
            .accessAllowed(true)
            .employmentStatus(EmploymentStatus.ACTIVE)
            .tokenVersion(3)
            .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateAccess(new UpdateUserAccessCommand(1L, false, "operator")))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("SYSTEM_ADMIN_ACCESS_REQUIRED");

        verify(userRepository, org.mockito.Mockito.never()).updateAccessAllowed(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyBoolean(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void userCreationCannotBypassRoleAssignmentHierarchy() {
        com.company.idm.domain.rbac.Role superAdminRole = com.company.idm.domain.rbac.Role.builder()
            .id(1L)
            .roleCode("SUPER_ADMIN")
            .permissionLevel(1)
            .status(1)
            .build();
        when(roleRepository.findByIds(java.util.List.of(1L))).thenReturn(java.util.List.of(superAdminRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new BizException("AUTH_FORBIDDEN", "无权分配当前角色"))
            .when(permissionLevelRuleService)
            .checkCanAssignRoles(eq("operator"), any(User.class), eq(java.util.List.of(superAdminRole)));

        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(
            "employee-new",
            "新员工",
            null,
            null,
            null,
            "E009",
            null,
            java.util.List.of(),
            true,
            java.util.List.of(1L),
            "operator"
        )))
            .isInstanceOf(BizException.class)
            .extracting(exception -> ((BizException) exception).getCode())
            .isEqualTo("AUTH_FORBIDDEN");

        verify(ldapDirectoryService, org.mockito.Mockito.never()).createUser(any(User.class), any());
        verify(userRepository, org.mockito.Mockito.never()).assignRoles(any(), any(), any());
    }

    private User user(boolean accessAllowed, EmploymentStatus employmentStatus, int tokenVersion) {
        return User.builder()
            .id(7L)
            .userId("zhangsan")
            .realName("张三")
            .accessAllowed(accessAllowed)
            .employmentStatus(employmentStatus)
            .tokenVersion(tokenVersion)
            .build();
    }
}
