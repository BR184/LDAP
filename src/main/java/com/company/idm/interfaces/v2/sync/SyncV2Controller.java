package com.company.idm.interfaces.v2.sync;

import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.interfaces.sync.SyncBatchDetailResponse;
import com.company.idm.interfaces.sync.SyncJobResponse;
import com.company.idm.interfaces.sync.SyncReconcileRequest;
import com.company.idm.interfaces.sync.SyncResponseAssembler;
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
 * V2 同步任务接口。
 */
@RestController
@RequestMapping("/api/v2/sync")
@RequiredArgsConstructor
public class SyncV2Controller {

    private final SyncApplicationService syncApplicationService;
    private final SyncResponseAssembler syncResponseAssembler;

    @PostMapping("/reconcile/preview")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'SYNC_RECONCILE_PREVIEW')")
    public ApiResponseV2<SyncBatchDetailResponse> previewReconcile(
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponseV2.ok(syncResponseAssembler.toResponse(syncApplicationService.previewReconcile(username, SyncTriggerMode.MANUAL)));
    }

    @PostMapping("/reconcile/execute")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'SYNC_RECONCILE_EXECUTE')")
    public ApiResponseV2<SyncBatchDetailResponse> executeReconcile(
        @RequestBody(required = false) SyncReconcileRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        boolean autoRepair = request != null && Boolean.TRUE.equals(request.autoRepair());
        return ApiResponseV2.ok(syncResponseAssembler.toResponse(syncApplicationService.executeReconcile(autoRepair, username, SyncTriggerMode.MANUAL)));
    }

    @PostMapping("/jobs/{id}/retry")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'SYNC_JOB_RETRY')")
    public ApiResponseV2<SyncBatchDetailResponse> retryJob(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponseV2.ok(syncResponseAssembler.toResponse(syncApplicationService.retryJob(id, username)));
    }

    @GetMapping("/jobs")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'SYNC_JOB_LIST')")
    public ApiResponseV2<List<SyncJobResponse>> listJobs() {
        return ApiResponseV2.ok(syncApplicationService.listJobs().stream().map(syncResponseAssembler::toJobResponse).toList());
    }

    @GetMapping("/batches/{batchNo}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'SYNC_BATCH_DETAIL')")
    public ApiResponseV2<SyncBatchDetailResponse> batchDetail(@PathVariable String batchNo) {
        return ApiResponseV2.ok(syncResponseAssembler.toResponse(syncApplicationService.getBatchDetail(batchNo)));
    }
}
