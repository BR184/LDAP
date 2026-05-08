package com.company.idm.application.sync.feishu;

import com.company.idm.common.enums.ImportMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 统一编排飞书一键导入流程。
 */
@Service
public class FeishuFullImportService {

    private final FeishuImportDocumentResolver importDocumentResolver;
    private final FeishuDepartmentImportService departmentImportService;
    private final FeishuUserImportService userImportService;
    private final FeishuImportAlignmentService alignmentService;

    public FeishuFullImportService(
        FeishuImportDocumentResolver importDocumentResolver,
        FeishuDepartmentImportService departmentImportService,
        FeishuUserImportService userImportService,
        FeishuImportAlignmentService alignmentService
    ) {
        this.importDocumentResolver = importDocumentResolver;
        this.departmentImportService = departmentImportService;
        this.userImportService = userImportService;
        this.alignmentService = alignmentService;
    }

    public FeishuFullImportResult executeFromDocument(String documentPath, ImportMode importMode) {
        FeishuFullImportDocument document = importDocumentResolver.resolveFullImportDocument(documentPath);
        FeishuDepartmentImportResult departmentResult = departmentImportService.executeFromPayloads(document.departments());
        FeishuUserImportResult userResult = userImportService.executeFromPayloads(document.users());

        List<com.company.idm.application.sync.SyncDiffPayload> alignmentDiffs = new ArrayList<>();
        if (importMode == ImportMode.ALIGN) {
            Set<String> retainedUserIds = document.users().stream()
                .map(FeishuUserPayload::userId)
                .collect(Collectors.toSet());
            Set<String> retainedDepartmentExternalIds = document.departments().stream()
                .map(FeishuDepartmentPayload::externalId)
                .collect(Collectors.toSet());
            alignmentDiffs.addAll(alignmentService.cleanupMissingUsers(retainedUserIds));
            alignmentDiffs.addAll(alignmentService.cleanupMissingDepartments(retainedDepartmentExternalIds));
        }

        return new FeishuFullImportResult(departmentResult, userResult, alignmentDiffs);
    }
}
