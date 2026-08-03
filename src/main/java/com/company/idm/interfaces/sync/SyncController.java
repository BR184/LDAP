package com.company.idm.interfaces.sync;

import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.application.sync.SyncBatchDetail;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.common.enums.SyncTriggerMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鎻愪緵鍚屾浠诲姟鐨勬墜宸ヨЕ鍙戜笌鏌ヨ鎺ュ彛銆? */
@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncApplicationService syncApplicationService;
    private final SyncResponseAssembler syncResponseAssembler;

    @PostMapping("/reconcile/preview")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'SYNC_RECONCILE_PREVIEW')")
    public ApiResponse<SyncBatchDetailResponse> previewReconcile(
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponse.success(syncResponseAssembler.toResponse(syncApplicationService.previewReconcile(username, SyncTriggerMode.MANUAL)));
    }

    @PostMapping("/reconcile/execute")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'SYNC_RECONCILE_EXECUTE')")
    public ApiResponse<SyncBatchDetailResponse> executeReconcile(
        @RequestBody(required = false) SyncReconcileRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        boolean autoRepair = request != null && Boolean.TRUE.equals(request.autoRepair());
        return ApiResponse.success(syncResponseAssembler.toResponse(syncApplicationService.executeReconcile(autoRepair, username, SyncTriggerMode.MANUAL)));
    }

    @PostMapping("/jobs/{id}/retry")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'SYNC_JOB_RETRY')")
    public ApiResponse<SyncBatchDetailResponse> retryJob(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponse.success(syncResponseAssembler.toResponse(syncApplicationService.retryJob(id, username)));
    }

    @GetMapping("/jobs")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'SYNC_JOB_LIST')")
    public ApiResponse<List<SyncJobResponse>> listJobs() {
        return ApiResponse.success(syncApplicationService.listJobs().stream().map(syncResponseAssembler::toJobResponse).toList());
    }

    @GetMapping("/batches/{batchNo}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'SYNC_BATCH_DETAIL')")
    public ApiResponse<SyncBatchDetailResponse> batchDetail(@PathVariable String batchNo) {
        return ApiResponse.success(syncResponseAssembler.toResponse(syncApplicationService.getBatchDetail(batchNo)));
    }
}

