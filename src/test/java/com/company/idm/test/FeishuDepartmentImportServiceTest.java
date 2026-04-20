package com.company.idm.test;

import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.application.sync.feishu.FeishuImportDocumentResolver;
import com.company.idm.application.sync.feishu.FeishuDepartmentImportResult;
import com.company.idm.application.sync.feishu.FeishuDepartmentImportService;
import com.company.idm.application.sync.feishu.FeishuDepartmentPayload;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.infrastructure.feishu.FeishuDepartmentRemoteService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证飞书部门导入服务的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class FeishuDepartmentImportServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private LdapGroupService ldapGroupService;

    @Mock
    private FeishuDepartmentRemoteService departmentRemoteService;

    @Mock
    private FeishuImportDocumentResolver importDocumentResolver;

    @Test
    void shouldPreviewNewDepartmentsSuccessfully() {
        FeishuDepartmentImportService service = buildService();
        when(departmentRepository.findAll()).thenReturn(List.of());
        when(departmentRemoteService.fetchDepartments()).thenReturn(List.of(
            new FeishuDepartmentPayload("ou_root", "D100", "研发中心", null, 1, 1),
            new FeishuDepartmentPayload("ou_child", "D101", "后端组", "ou_root", 1, 2)
        ));

        FeishuDepartmentImportResult result = service.preview(new SyncRequestPayload(
            false, null, false, "admin", SyncTriggerMode.MANUAL, null
        ));

        assertThat(result.newCount()).isEqualTo(2);
        assertThat(result.updateCount()).isZero();
        assertThat(result.diffs()).hasSize(2);
        assertThat(result.diffs().get(0).diffType()).isEqualTo(SyncDiffType.MISSING_IN_MYSQL);
    }

    @Test
    void shouldExecuteDepartmentImportAndBuildTree() {
        FeishuDepartmentImportService service = buildService();
        when(departmentRepository.findAll()).thenReturn(List.of());
        when(departmentRepository.save(any(Department.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ldapGroupService.existsGroup("D100")).thenReturn(false);
        when(ldapGroupService.existsGroup("D101")).thenReturn(false);
        when(ldapGroupService.createGroup("D100", "研发中心")).thenReturn("cn=D100_研发中心,ou=groups,dc=corp,dc=local");
        when(ldapGroupService.createGroup("D101", "后端组")).thenReturn("cn=D101_后端组,ou=groups,dc=corp,dc=local");
        when(departmentRemoteService.fetchDepartments()).thenReturn(List.of(
            new FeishuDepartmentPayload("ou_root", "D100", "研发中心", null, 1, 1),
            new FeishuDepartmentPayload("ou_child", "D101", "后端组", "ou_root", 1, 2)
        ));

        FeishuDepartmentImportResult result = service.execute(new SyncRequestPayload(
            false, null, false, "admin", SyncTriggerMode.MANUAL, null
        ));

        assertThat(result.newCount()).isEqualTo(2);
        assertThat(result.diffs()).isEmpty();
        verify(departmentRepository, atLeastOnce()).save(argThat(dept ->
            "D100".equals(dept.getDeptCode()) && "/D100".equals(dept.getAncestorPath()) && dept.getDeptLevel() == 1
        ));
        verify(departmentRepository, atLeastOnce()).save(argThat(dept ->
            "D101".equals(dept.getDeptCode())
                && "D100".equals(dept.getParentDeptCode())
                && "/D100/D101".equals(dept.getAncestorPath())
                && dept.getDeptLevel() == 2
        ));
        verify(departmentRepository, atLeastOnce()).save(argThat(dept ->
            "D100".equals(dept.getDeptCode()) && "cn=D100_研发中心,ou=groups,dc=corp,dc=local".equals(dept.getLdapDn())
        ));
        verify(departmentRepository, atLeastOnce()).save(argThat(dept ->
            "D101".equals(dept.getDeptCode()) && "cn=D101_后端组,ou=groups,dc=corp,dc=local".equals(dept.getLdapDn())
        ));
    }

    @Test
    void shouldRejectWhenParentExternalIdMissing() {
        FeishuDepartmentImportService service = buildService();
        when(departmentRepository.findAll()).thenReturn(List.of());
        when(departmentRemoteService.fetchDepartments()).thenReturn(List.of(
            new FeishuDepartmentPayload("ou_child", "D101", "后端组", "ou_root_missing", 1, 1)
        ));

        assertThatThrownBy(() -> service.preview(new SyncRequestPayload(
            false, null, false, "admin", SyncTriggerMode.MANUAL, null
        )))
            .isInstanceOf(BizException.class)
            .hasMessage("飞书部门父节点不存在");
    }

    @Test
    void shouldRejectWhenExternalIdAndDeptCodeConflict() {
        FeishuDepartmentImportService service = buildService();
        Department existing = Department.builder()
            .id(1L)
            .deptCode("D100")
            .deptName("旧部门")
            .ancestorPath("/D100")
            .deptLevel(1)
            .sourceType(SourceType.FEISHU)
            .externalId("ou_root")
            .status(1)
            .build();
        when(departmentRepository.findAll()).thenReturn(List.of(existing));
        when(departmentRemoteService.fetchDepartments()).thenReturn(List.of(
            new FeishuDepartmentPayload("ou_root", "D999", "研发中心", null, 1, 1)
        ));

        assertThatThrownBy(() -> service.preview(new SyncRequestPayload(
            false, null, false, "admin", SyncTriggerMode.MANUAL, null
        )))
            .isInstanceOf(BizException.class)
            .hasMessage("飞书部门 external_id 与 dept_code 映射冲突");
    }

    @Test
    void shouldPreviewDepartmentsFromDocumentPath() {
        FeishuDepartmentImportService service = buildService();
        when(departmentRepository.findAll()).thenReturn(List.of());
        when(importDocumentResolver.resolveDepartments("departments/demo.json")).thenReturn(List.of(
            new FeishuDepartmentPayload("ou_root", "D100", "研发中心", null, 1, 1)
        ));

        FeishuDepartmentImportResult result = service.previewFromDocument("departments/demo.json");

        assertThat(result.newCount()).isEqualTo(1);
        assertThat(result.diffs()).singleElement().satisfies(diff ->
            assertThat(diff.diffType()).isEqualTo(SyncDiffType.MISSING_IN_MYSQL)
        );
    }

    private FeishuDepartmentImportService buildService() {
        return new FeishuDepartmentImportService(
            departmentRemoteService,
            importDocumentResolver,
            departmentRepository,
            ldapGroupService
        );
    }
}
