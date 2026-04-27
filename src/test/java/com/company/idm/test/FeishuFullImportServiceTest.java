package com.company.idm.test;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.feishu.FeishuDepartmentImportResult;
import com.company.idm.application.sync.feishu.FeishuDepartmentImportService;
import com.company.idm.application.sync.feishu.FeishuDepartmentPayload;
import com.company.idm.application.sync.feishu.FeishuFullImportDocument;
import com.company.idm.application.sync.feishu.FeishuFullImportResult;
import com.company.idm.application.sync.feishu.FeishuFullImportService;
import com.company.idm.application.sync.feishu.FeishuImportAlignmentService;
import com.company.idm.application.sync.feishu.FeishuImportDocumentResolver;
import com.company.idm.application.sync.feishu.FeishuUserImportResult;
import com.company.idm.application.sync.feishu.FeishuUserImportService;
import com.company.idm.application.sync.feishu.FeishuUserPayload;
import com.company.idm.common.enums.ImportMode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuFullImportServiceTest {

    @Mock
    private FeishuImportDocumentResolver importDocumentResolver;

    @Mock
    private FeishuDepartmentImportService departmentImportService;

    @Mock
    private FeishuUserImportService userImportService;

    @Mock
    private FeishuImportAlignmentService alignmentService;

    @Test
    void shouldExecuteSupplementImportWithoutCleanup() {
        FeishuFullImportService service = buildService();
        FeishuFullImportDocument document = buildDocument();
        when(importDocumentResolver.resolveFullImportDocument("bundle/full-demo.json")).thenReturn(document);
        when(departmentImportService.executeFromPayloads(document.departments()))
            .thenReturn(new FeishuDepartmentImportResult(1, 0, 0, List.of()));
        when(userImportService.executeFromPayloads(document.users()))
            .thenReturn(new FeishuUserImportResult(1, 0, 0, List.of()));

        FeishuFullImportResult result = service.executeFromDocument("bundle/full-demo.json", ImportMode.SUPPLEMENT);

        assertThat(result.departmentImportResult().newCount()).isEqualTo(1);
        assertThat(result.userImportResult().newCount()).isEqualTo(1);
        verify(alignmentService, never()).cleanupMissingUsers(anySet());
        verify(alignmentService, never()).cleanupMissingDepartments(anySet());
    }

    @Test
    void shouldExecuteAlignImportAndCleanupUsersBeforeDepartments() {
        FeishuFullImportService service = buildService();
        FeishuFullImportDocument document = buildDocument();
        when(importDocumentResolver.resolveFullImportDocument("bundle/full-demo.xlsx")).thenReturn(document);
        when(departmentImportService.executeFromPayloads(document.departments()))
            .thenReturn(new FeishuDepartmentImportResult(0, 1, 0, List.of()));
        when(userImportService.executeFromPayloads(document.users()))
            .thenReturn(new FeishuUserImportResult(0, 1, 0, List.of()));
        when(alignmentService.cleanupMissingUsers(Set.of("u001")))
            .thenReturn(List.<SyncDiffPayload>of());
        when(alignmentService.cleanupMissingDepartments(Set.of("ou_root")))
            .thenReturn(List.<SyncDiffPayload>of());

        FeishuFullImportResult result = service.executeFromDocument("bundle/full-demo.xlsx", ImportMode.ALIGN);

        assertThat(result.alignmentDiffs()).isEmpty();
        InOrder inOrder = inOrder(departmentImportService, userImportService, alignmentService);
        inOrder.verify(departmentImportService).executeFromPayloads(document.departments());
        inOrder.verify(userImportService).executeFromPayloads(document.users());
        inOrder.verify(alignmentService).cleanupMissingUsers(Set.of("u001"));
        inOrder.verify(alignmentService).cleanupMissingDepartments(Set.of("ou_root"));
    }

    private FeishuFullImportService buildService() {
        return new FeishuFullImportService(
            importDocumentResolver,
            departmentImportService,
            userImportService,
            alignmentService
        );
    }

    private FeishuFullImportDocument buildDocument() {
        return new FeishuFullImportDocument(
            List.of(new FeishuDepartmentPayload("ou_root", "D100", "研发中心", null, 1, 1)),
            List.of(new FeishuUserPayload("u001", "zhangsan", "张三", "zhangsan@corp.local", "13900000000", "E10001", "ou_root", 1, 1))
        );
    }
}
