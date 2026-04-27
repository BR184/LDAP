package com.company.idm.interfaces.system;

import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.interfaces.sync.SyncBatchDetailResponse;
import com.company.idm.interfaces.sync.SyncResponseAssembler;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供系统管理下的导入入口。
 */
@RestController
@RequestMapping("/api/v1/system/imports")
public class SystemImportController {

    private final SyncApplicationService syncApplicationService;
    private final SyncResponseAssembler syncResponseAssembler;

    public SystemImportController(
        SyncApplicationService syncApplicationService,
        SyncResponseAssembler syncResponseAssembler
    ) {
        this.syncApplicationService = syncApplicationService;
        this.syncResponseAssembler = syncResponseAssembler;
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
}
