package com.company.idm.interfaces.sync;

import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.application.sync.SyncBatchDetail;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.domain.sync.SyncBatch;
import com.company.idm.domain.sync.SyncDiff;
import com.company.idm.domain.sync.SyncJob;
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
 * 提供同步任务的手工触发与查询接口。
 */
@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncApplicationService syncApplicationService;

    @PostMapping("/feishu/preview")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/sync/feishu/preview', 'POST')")
    public ApiResponse<SyncBatchDetailResponse> previewFeishu(
        @RequestBody(required = false) SyncFeishuTriggerRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        SyncBatchDetail detail = syncApplicationService.previewFeishu(
            request == null ? null : request.sourceFileName(),
            request == null ? null : request.sourceFileHash(),
            username,
            SyncTriggerMode.MANUAL
        );
        return ApiResponse.success(toResponse(detail));
    }

    @PostMapping("/feishu/execute")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/sync/feishu/execute', 'POST')")
    public ApiResponse<SyncBatchDetailResponse> executeFeishu(
        @RequestBody(required = false) SyncFeishuTriggerRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        SyncBatchDetail detail = syncApplicationService.executeFeishu(
            request == null ? null : request.sourceFileName(),
            request == null ? null : request.sourceFileHash(),
            username,
            SyncTriggerMode.MANUAL
        );
        return ApiResponse.success(toResponse(detail));
    }

    @PostMapping("/reconcile/preview")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/sync/reconcile/preview', 'POST')")
    public ApiResponse<SyncBatchDetailResponse> previewReconcile(
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        return ApiResponse.success(toResponse(syncApplicationService.previewReconcile(username, SyncTriggerMode.MANUAL)));
    }

    @PostMapping("/reconcile/execute")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/sync/reconcile/execute', 'POST')")
    public ApiResponse<SyncBatchDetailResponse> executeReconcile(
        @RequestBody(required = false) SyncReconcileRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        boolean autoRepair = request != null && Boolean.TRUE.equals(request.autoRepair());
        return ApiResponse.success(toResponse(syncApplicationService.executeReconcile(autoRepair, username, SyncTriggerMode.MANUAL)));
    }

    @PostMapping("/jobs/{id}/retry")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/sync/jobs/' + #id + '/retry', 'POST')")
    public ApiResponse<SyncBatchDetailResponse> retryJob(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        return ApiResponse.success(toResponse(syncApplicationService.retryJob(id, username)));
    }

    @GetMapping("/jobs")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/sync/jobs', 'GET')")
    public ApiResponse<List<SyncJobResponse>> listJobs() {
        return ApiResponse.success(syncApplicationService.listJobs().stream().map(this::toJobResponse).toList());
    }

    @GetMapping("/batches/{batchNo}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/sync/batches/' + #batchNo, 'GET')")
    public ApiResponse<SyncBatchDetailResponse> batchDetail(@PathVariable String batchNo) {
        return ApiResponse.success(toResponse(syncApplicationService.getBatchDetail(batchNo)));
    }

    private SyncBatchDetailResponse toResponse(SyncBatchDetail detail) {
        return new SyncBatchDetailResponse(
            toBatchResponse(detail.batch()),
            detail.jobs().stream().map(this::toJobResponse).toList(),
            detail.diffs().stream().map(this::toDiffResponse).toList()
        );
    }

    private SyncBatchResponse toBatchResponse(SyncBatch batch) {
        return new SyncBatchResponse(
            batch.getId(),
            batch.getBatchNo(),
            batch.getBatchType().name(),
            batch.getSourceType().name(),
            batch.getTriggerMode().name(),
            batch.getStatus().name(),
            batch.getFileName(),
            batch.getFileHash(),
            batch.getSummaryJson(),
            batch.getOperator(),
            batch.getCorrelationBatchNo()
        );
    }

    private SyncJobResponse toJobResponse(SyncJob job) {
        return new SyncJobResponse(
            job.getId(),
            job.getBatchNo(),
            job.getJobType().name(),
            job.getTargetType().name(),
            job.getStatus().name(),
            job.getRequestJson(),
            job.getResultJson(),
            job.getErrorMessage(),
            job.getOperator(),
            job.getRetryCount()
        );
    }

    private SyncDiffResponse toDiffResponse(SyncDiff diff) {
        return new SyncDiffResponse(
            diff.getId(),
            diff.getBatchNo(),
            diff.getJobId(),
            diff.getTargetType().name(),
            diff.getTargetKey(),
            diff.getDiffType().name(),
            diff.getSourceSnapshot(),
            diff.getTargetSnapshot(),
            diff.getRepairable(),
            diff.getStatus().name()
        );
    }
}
