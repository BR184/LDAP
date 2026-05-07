package com.company.idm.interfaces.system;

import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.application.sync.feishu.FeishuImportUploadService;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.common.enums.ImportMode;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.interfaces.sync.SyncBatchDetailResponse;
import com.company.idm.interfaces.sync.SyncResponseAssembler;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 提供系统管理下的导入入口。
 */
@RestController
@RequestMapping("/api/v1/system/imports")
public class SystemImportController {

    private final SyncApplicationService syncApplicationService;
    private final SyncResponseAssembler syncResponseAssembler;
    private final FeishuImportUploadService feishuImportUploadService;

    public SystemImportController(
        SyncApplicationService syncApplicationService,
        SyncResponseAssembler syncResponseAssembler,
        FeishuImportUploadService feishuImportUploadService
    ) {
        this.syncApplicationService = syncApplicationService;
        this.syncResponseAssembler = syncResponseAssembler;
        this.feishuImportUploadService = feishuImportUploadService;
    }

    @PostMapping("/feishu/full")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/system/imports/feishu/full', 'POST')")
    public ApiResponse<SyncBatchDetailResponse> importFeishuFullFile(
        @Valid @RequestBody FeishuFullImportRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        return ApiResponse.success(syncResponseAssembler.toResponse(
            syncApplicationService.executeFeishuFullFileImport(
                request.documentPath(),
                request.importMode(),
                request.remark(),
                username,
                SyncTriggerMode.MANUAL
            )
        ));
    }

    @PostMapping("/feishu/full/upload")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/system/imports/feishu/full', 'POST')")
    public ApiResponse<SyncBatchDetailResponse> importFeishuFullFileByUpload(
        @RequestParam("file") MultipartFile file,
        @RequestParam("importMode") ImportMode importMode,
        @RequestParam(value = "remark", required = false) String remark,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        String storedDocumentPath = feishuImportUploadService.store(file);
        return ApiResponse.success(syncResponseAssembler.toResponse(
            syncApplicationService.executeFeishuFullFileImport(
                storedDocumentPath,
                importMode,
                remark,
                username,
                SyncTriggerMode.MANUAL
            )
        ));
    }
}
