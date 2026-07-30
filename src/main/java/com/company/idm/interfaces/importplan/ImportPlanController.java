package com.company.idm.interfaces.importplan;

import com.company.idm.application.sync.importplan.ImportConsistencyService;
import com.company.idm.application.sync.importplan.ImportExecutionApplicationService;
import com.company.idm.application.sync.importplan.ImportPlanApplicationService;
import com.company.idm.application.sync.importplan.ImportRollbackApplicationService;
import com.company.idm.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
public class ImportPlanController {

    private final ImportPlanApplicationService importPlanService;
    private final ImportExecutionApplicationService executionService;
    private final ImportRollbackApplicationService rollbackService;
    private final ImportConsistencyService consistencyService;
    private final ImportPlanResponseAssembler assembler;

    @PostMapping("/plan")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/import/plan', 'POST')")
    public ApiResponse<ImportBatchDetailResponse> generatePlan(
        @Valid @RequestBody GenerateImportPlanRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponse.success(assembler.toDetailResponse(
            importPlanService.generatePlan(request.documentPath(), username, request.remark())
        ));
    }

    @PostMapping("/plan/upload")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/import/plan', 'POST')")
    public ApiResponse<ImportBatchDetailResponse> generatePlanByUpload(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "remark", required = false) String remark,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponse.success(assembler.toDetailResponse(
            importPlanService.generatePlanFromUpload(file, username, remark)
        ));
    }

    @GetMapping("/plan")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/import/plan', 'GET')")
    public ApiResponse<List<ImportBatchResponse>> listPlans(@RequestParam(defaultValue = "50") Integer limit) {
        return ApiResponse.success(importPlanService.listRecent(limit).stream()
            .map(assembler::toBatchResponse)
            .toList());
    }

    @GetMapping("/plan/{batchId}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/import/plan/' + #batchId, 'GET')")
    public ApiResponse<ImportBatchDetailResponse> getPlanDetail(@PathVariable Long batchId) {
        return ApiResponse.success(assembler.toDetailResponse(importPlanService.findById(batchId)));
    }

    @GetMapping("/plan/{batchId}/review")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/import/plan/' + #batchId, 'GET')")
    public ApiResponse<ImportPlanReviewResponse> getPlanReview(@PathVariable Long batchId) {
        return ApiResponse.success(assembler.toReviewResponse(importPlanService.findById(batchId)));
    }

    @PostMapping("/plan/{batchId}/confirm")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/import/plan/' + #batchId + '/confirm', 'POST')")
    public ApiResponse<ImportBatchDetailResponse> confirmPlan(
        @PathVariable Long batchId,
        @RequestBody(required = false) ConfirmImportPlanRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        List<Long> enabledItemIds = request == null ? null : request.enabledItemIds();
        List<Long> confirmedItemIds = request == null ? null : request.confirmedItemIds();
        return ApiResponse.success(assembler.toDetailResponse(
            importPlanService.confirmPlan(batchId, enabledItemIds, confirmedItemIds, username)
        ));
    }

    @PostMapping("/plan/{batchId}/conflicts/{itemId}/skip")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/import/plan/' + #batchId + '/conflicts/' + #itemId + '/skip', 'POST')")
    public ApiResponse<ImportBatchDetailResponse> resolveConflictBySkipping(
        @PathVariable Long batchId,
        @PathVariable Long itemId,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponse.success(assembler.toDetailResponse(
            importPlanService.resolveConflictBySkipping(batchId, itemId, username)
        ));
    }

    @PostMapping("/plan/{batchId}/conflicts/{itemId}/merge-by-employee-no")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/import/plan/' + #batchId + '/conflicts/' + #itemId + '/merge-by-employee-no', 'POST')")
    public ApiResponse<ImportBatchDetailResponse> resolveConflictByMergingEmployeeNumber(
        @PathVariable Long batchId,
        @PathVariable Long itemId,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponse.success(assembler.toDetailResponse(
            importPlanService.resolveConflictByMergingEmployeeNumber(batchId, itemId, username)
        ));
    }

    @PostMapping("/plan/{batchId}/cancel")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/import/plan/' + #batchId + '/cancel', 'POST')")
    public ApiResponse<ImportBatchDetailResponse> cancelPlan(
        @PathVariable Long batchId,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponse.success(assembler.toDetailResponse(importPlanService.cancelPlan(batchId, username)));
    }

    @PostMapping("/plan/{batchId}/execute")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/import/plan/' + #batchId + '/execute', 'POST')")
    public ApiResponse<ImportBatchDetailResponse> executePlan(
        @PathVariable Long batchId,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponse.success(assembler.toDetailResponse(executionService.executePlan(batchId, username)));
    }

    @PostMapping("/plan/{batchId}/rollback-plan")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/import/plan/' + #batchId + '/rollback', 'POST')")
    public ApiResponse<ImportBatchDetailResponse> generateRollbackPlan(@PathVariable Long batchId) {
        return ApiResponse.success(assembler.toDetailResponse(rollbackService.generateRollbackPlan(batchId)));
    }

    @PostMapping("/plan/{batchId}/rollback")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/import/plan/' + #batchId + '/rollback', 'POST')")
    public ApiResponse<ImportBatchDetailResponse> rollbackPlan(
        @PathVariable Long batchId,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponse.success(assembler.toDetailResponse(rollbackService.executeRollback(batchId, username)));
    }

    @PostMapping("/plan/{batchId}/retry-ldap")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/import/plan/' + #batchId + '/execute', 'POST')")
    public ApiResponse<ImportBatchDetailResponse> retryLdapFailures(
        @PathVariable Long batchId,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponse.success(assembler.toDetailResponse(consistencyService.retryLdapFailures(batchId, username)));
    }
}
