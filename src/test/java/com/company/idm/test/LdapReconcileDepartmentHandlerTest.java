package com.company.idm.test;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.SyncJobExecutionResult;
import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.application.sync.handler.LdapReconcileDepartmentHandler;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapGroupService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证部门维度 LDAP 对账处理器的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class LdapReconcileDepartmentHandlerTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private LdapGroupService ldapGroupService;

    @Test
    void shouldReportMissingDepartmentGroupInPreview() {
        Department department = Department.builder()
            .id(1L)
            .deptCode("D001")
            .deptName("研发中心")
            .sourceType(SourceType.MANUAL)
            .status(1)
            .build();
        when(departmentRepository.findAll()).thenReturn(List.of(department));
        when(ldapGroupService.existsGroup("D001")).thenReturn(false);
        LdapReconcileDepartmentHandler handler = new LdapReconcileDepartmentHandler(departmentRepository, ldapGroupService);

        SyncJobExecutionResult result = handler.preview(new SyncRequestPayload(null, null, false, "admin", SyncTriggerMode.MANUAL));

        assertThat(result.diffs()).hasSize(1);
        SyncDiffPayload diff = result.diffs().get(0);
        assertThat(diff.diffType()).isEqualTo(SyncDiffType.MISSING_IN_LDAP);
        assertThat(diff.targetKey()).isEqualTo("D001");
    }

    @Test
    void shouldRepairDepartmentGroupWhenAutoRepairEnabled() {
        Department department = Department.builder()
            .id(1L)
            .deptCode("D001")
            .deptName("研发中心")
            .sourceType(SourceType.MANUAL)
            .status(1)
            .build();
        when(departmentRepository.findAll()).thenReturn(List.of(department));
        when(ldapGroupService.existsGroup("D001")).thenReturn(false);
        when(ldapGroupService.createGroup("D001", "研发中心")).thenReturn("cn=D001_研发中心,ou=groups,dc=corp,dc=local");
        LdapReconcileDepartmentHandler handler = new LdapReconcileDepartmentHandler(departmentRepository, ldapGroupService);

        SyncJobExecutionResult result = handler.execute(new SyncRequestPayload(null, null, true, "admin", SyncTriggerMode.MANUAL));

        assertThat(result.diffs()).isEmpty();
        verify(departmentRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
            "D001".equals(saved.getDeptCode()) && "cn=D001_研发中心,ou=groups,dc=corp,dc=local".equals(saved.getLdapDn())
        ));
    }
}
