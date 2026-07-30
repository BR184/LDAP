package com.company.idm.application.user;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.common.enums.EmploymentStatus;
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
    private final UserApplicationService service = new UserApplicationService(
        userRepository,
        mock(DepartmentRepository.class),
        ldapDirectoryService,
        mock(LdapGroupService.class),
        mock(AuditLogRepository.class),
        mock(PolicyRefreshService.class),
        mock(PasswordPolicyValidator.class),
        permissionLevelRuleService,
        mock(RoleRepository.class),
        mock(IntranetEmailGenerationService.class),
        mock(AppLdapProperties.class),
        mock(PasswordVerificationTokenService.class),
        mock(InitialPasswordPolicy.class),
        new UserAccessPolicy()
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
