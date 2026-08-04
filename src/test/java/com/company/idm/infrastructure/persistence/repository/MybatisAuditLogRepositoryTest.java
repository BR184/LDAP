package com.company.idm.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.company.idm.domain.audit.AuditLog;
import com.company.idm.infrastructure.persistence.dataobject.AuditLogDO;
import com.company.idm.infrastructure.persistence.mapper.AuditLogMapper;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import com.company.idm.domain.token.PersonalAccessTokenScopeMode;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class MybatisAuditLogRepositoryTest {

    private final AuditLogMapper mapper = mock(AuditLogMapper.class);
    private final MybatisAuditLogRepository repository = new MybatisAuditLogRepository(mapper);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enrichesExistingBusinessAuditWithCredentialIdentity() {
        AuthenticatedUser principal = new AuthenticatedUser(
            7L,
            "employee",
            0,
            Set.of(),
            CredentialType.PERSONAL_ACCESS_TOKEN,
            11L,
            Set.of("USER_READ"),
            PersonalAccessTokenScopeMode.FIXED
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, Set.of())
        );

        repository.save(AuditLog.builder()
            .operator("employee")
            .operationType("USER_READ")
            .result("SUCCESS")
            .build());

        ArgumentCaptor<AuditLogDO> captor = ArgumentCaptor.forClass(AuditLogDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getCredentialType()).isEqualTo("PERSONAL_ACCESS_TOKEN");
        assertThat(captor.getValue().getCredentialId()).isEqualTo(11L);
    }
}
