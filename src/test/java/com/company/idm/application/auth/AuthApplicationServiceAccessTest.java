package com.company.idm.application.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.company.idm.application.department.DepartmentPathService;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserAccessPolicy;
import com.company.idm.domain.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthApplicationServiceAccessTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final LdapDirectoryService ldapDirectoryService = mock(LdapDirectoryService.class);
    private final AuthApplicationService service = new AuthApplicationService(
        userRepository,
        ldapDirectoryService,
        mock(TokenService.class),
        mock(AuditLogRepository.class),
        mock(DepartmentRepository.class),
        mock(DepartmentPathService.class),
        new UserAccessPolicy()
    );

    @Test
    void rejectsAdministratorDeniedUserBeforeLdapAuthentication() {
        when(userRepository.findByUserId("zhangsan")).thenReturn(Optional.of(user(false, EmploymentStatus.ACTIVE)));

        assertThatThrownBy(() -> service.login(new LoginCommand("zhangsan", "password")))
            .isInstanceOfSatisfying(BizException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getCode()).isEqualTo("AUTH_DISABLED"));

        verifyNoInteractions(ldapDirectoryService);
    }

    @Test
    void rejectsResignedUserBeforeLdapAuthentication() {
        when(userRepository.findByUserId("zhangsan")).thenReturn(Optional.of(user(true, EmploymentStatus.RESIGNED)));

        assertThatThrownBy(() -> service.login(new LoginCommand("zhangsan", "password")))
            .isInstanceOf(BizException.class);

        verifyNoInteractions(ldapDirectoryService);
    }

    private User user(boolean accessAllowed, EmploymentStatus employmentStatus) {
        return User.builder()
            .id(1L)
            .userId("zhangsan")
            .accessAllowed(accessAllowed)
            .employmentStatus(employmentStatus)
            .tokenVersion(0)
            .build();
    }
}
