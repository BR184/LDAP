package com.company.idm.test;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.SyncJobExecutionResult;
import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.application.sync.handler.LdapReconcileMembershipHandler;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证成员关系维度 LDAP 对账处理器的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class LdapReconcileMembershipHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private LdapGroupService ldapGroupService;

    @Test
    void shouldReportMembershipMismatch() {
        User user = User.builder()
            .id(1L)
            .username("zhangsan")
            .deptCode("D002")
            .status(UserStatus.ENABLED)
            .sourceType(SourceType.FEISHU)
            .roleCodes(Set.of("NORMAL_USER"))
            .build();
        Department department = Department.builder()
            .id(2L).deptCode("D002").deptName("运维部").status(1).sourceType(SourceType.FEISHU).build();
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(departmentRepository.findByDeptCode("D002")).thenReturn(Optional.of(department));
        when(ldapGroupService.listUserGroups("zhangsan")).thenReturn(List.of("D001"));

        LdapReconcileMembershipHandler handler = new LdapReconcileMembershipHandler(userRepository, departmentRepository, ldapGroupService);

        SyncJobExecutionResult result = handler.preview(new SyncRequestPayload(false, null, false, "admin", SyncTriggerMode.MANUAL));

        assertThat(result.diffs()).hasSize(1);
        SyncDiffPayload diff = result.diffs().get(0);
        assertThat(diff.diffType()).isEqualTo(SyncDiffType.RELATION_MISMATCH);
    }

    @Test
    void shouldRepairMembershipMismatch() {
        User user = User.builder()
            .id(1L)
            .username("zhangsan")
            .deptCode("D002")
            .status(UserStatus.ENABLED)
            .sourceType(SourceType.FEISHU)
            .roleCodes(Set.of("NORMAL_USER"))
            .build();
        Department department = Department.builder()
            .id(2L).deptCode("D002").deptName("运维部").status(1).sourceType(SourceType.FEISHU).build();
        when(userRepository.findAll()).thenReturn(List.of(user));
        when(departmentRepository.findByDeptCode("D002")).thenReturn(Optional.of(department));
        when(ldapGroupService.listUserGroups("zhangsan")).thenReturn(List.of("D001"));
        when(ldapGroupService.existsGroup("D002")).thenReturn(true);

        LdapReconcileMembershipHandler handler = new LdapReconcileMembershipHandler(userRepository, departmentRepository, ldapGroupService);

        SyncJobExecutionResult result = handler.execute(new SyncRequestPayload(false, null, true, "admin", SyncTriggerMode.MANUAL));

        assertThat(result.diffs()).isEmpty();
        verify(ldapGroupService).syncUserGroups("zhangsan", List.of("D002"));
    }
}
