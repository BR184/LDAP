package com.company.idm.test;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.SyncJobExecutionResult;
import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.application.sync.handler.LdapReconcileUserHandler;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapUserSnapshot;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.AppLdapProperties;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证用户维度 LDAP 对账处理器的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class LdapReconcileUserHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LdapDirectoryService ldapDirectoryService;

    @Test
    void shouldReportFieldMismatchInPreview() {
        User user = User.builder()
            .id(1L)
            .username("zhangsan")
            .realName("张三")
            .email("zhangsan@corp.local")
            .mobile("13800000000")
            .employeeNo("E10001")
            .deptCode("D001")
            .status(UserStatus.ENABLED)
            .sourceType(SourceType.FEISHU)
            .ldapDn("uid=zhangsan,ou=people,dc=corp,dc=local")
            .roleCodes(Set.of("NORMAL_USER"))
            .build();
        AppLdapProperties ldapProperties = new AppLdapProperties();
        ldapProperties.setBaseDn("dc=corp,dc=local");
        ldapProperties.setPeopleOu("ou=people");
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(ldapDirectoryService.existsByUid("zhangsan")).thenReturn(true);
        when(ldapDirectoryService.findUserSnapshot("zhangsan")).thenReturn(LdapUserSnapshot.builder()
            .username("zhangsan")
            .realName("老张三")
            .email("old@corp.local")
            .mobile("13999999999")
            .employeeNo("E99999")
            .deptCode("D009")
            .status("ENABLED")
            .dn("uid=zhangsan,ou=people,dc=corp,dc=local")
            .build());
        when(ldapDirectoryService.listAllUsernames()).thenReturn(List.of("zhangsan"));

        LdapReconcileUserHandler handler = new LdapReconcileUserHandler(userRepository, ldapDirectoryService, ldapProperties);

        SyncJobExecutionResult result = handler.preview(new SyncRequestPayload(false, null, false, "admin", SyncTriggerMode.MANUAL));

        assertThat(result.diffs()).hasSize(1);
        SyncDiffPayload diff = result.diffs().get(0);
        assertThat(diff.diffType()).isEqualTo(SyncDiffType.FIELD_MISMATCH);
        assertThat(diff.targetKey()).isEqualTo("zhangsan");
    }

    @Test
    void shouldRepairMissingUserInLdap() {
        User user = User.builder()
            .id(1L)
            .username("zhangsan")
            .realName("张三")
            .email("zhangsan@corp.local")
            .mobile("13800000000")
            .employeeNo("E10001")
            .deptCode("D001")
            .status(UserStatus.DISABLED)
            .sourceType(SourceType.FEISHU)
            .ldapDn("uid=zhangsan,ou=people,dc=corp,dc=local")
            .roleCodes(Set.of("NORMAL_USER"))
            .build();
        AppLdapProperties ldapProperties = new AppLdapProperties();
        ldapProperties.setBaseDn("dc=corp,dc=local");
        ldapProperties.setPeopleOu("ou=people");
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(ldapDirectoryService.existsByUid("zhangsan")).thenReturn(false);
        when(ldapDirectoryService.createUser(user, "123456")).thenReturn("uid=zhangsan,ou=people,dc=corp,dc=local");
        when(ldapDirectoryService.listAllUsernames()).thenReturn(List.of());

        LdapReconcileUserHandler handler = new LdapReconcileUserHandler(userRepository, ldapDirectoryService, ldapProperties);

        SyncJobExecutionResult result = handler.execute(new SyncRequestPayload(false, null, true, "admin", SyncTriggerMode.MANUAL));

        assertThat(result.diffs()).isEmpty();
        verify(ldapDirectoryService).createUser(user, "123456");
        verify(ldapDirectoryService).disableUser("zhangsan");
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
            "zhangsan".equals(saved.getUsername()) && "uid=zhangsan,ou=people,dc=corp,dc=local".equals(saved.getLdapDn())
        ));
    }
}
