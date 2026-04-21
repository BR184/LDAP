package com.company.idm.test;

import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.application.sync.SyncBatchDetail;
import com.company.idm.domain.sync.SyncJob;
import com.company.idm.interfaces.sync.SyncBatchDetailResponse;
import com.company.idm.interfaces.sync.SyncBatchResponse;
import com.company.idm.interfaces.sync.SyncController;
import com.company.idm.interfaces.sync.SyncJobResponse;
import com.company.idm.interfaces.sync.SyncResponseAssembler;
import com.company.idm.test.support.AbstractControllerMvcTest;
import com.company.idm.test.support.ControllerMvcSlice;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SyncController.class)
@ControllerMvcSlice
@Import(SyncController.class)
class SyncControllerTest extends AbstractControllerMvcTest {

    @MockBean
    private SyncApplicationService syncApplicationService;

    @MockBean
    private SyncResponseAssembler syncResponseAssembler;

    @Test
    @WithMockUser(username = "admin")
    void shouldPreviewReconcileSuccessfully() throws Exception {
        allow("/api/v1/sync/reconcile/preview", "POST");
        SyncBatchDetail detail = mock(SyncBatchDetail.class);
        when(syncApplicationService.previewReconcile("admin", com.company.idm.common.enums.SyncTriggerMode.MANUAL)).thenReturn(detail);
        when(syncResponseAssembler.toResponse(detail)).thenReturn(buildDetailResponse("PREVIEW_BATCH"));

        mockMvc.perform(post("/api/v1/sync/reconcile/preview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batch.batchNo").value("PREVIEW_BATCH"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldExecuteReconcileSuccessfully() throws Exception {
        allow("/api/v1/sync/reconcile/execute", "POST");
        SyncBatchDetail detail = mock(SyncBatchDetail.class);
        when(syncApplicationService.executeReconcile(true, "admin", com.company.idm.common.enums.SyncTriggerMode.MANUAL)).thenReturn(detail);
        when(syncResponseAssembler.toResponse(detail)).thenReturn(buildDetailResponse("EXECUTE_BATCH"));

        mockMvc.perform(post("/api/v1/sync/reconcile/execute")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "autoRepair": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batch.batchNo").value("EXECUTE_BATCH"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldRetryJobSuccessfully() throws Exception {
        allow("/api/v1/sync/jobs/1/retry", "POST");
        SyncBatchDetail detail = mock(SyncBatchDetail.class);
        when(syncApplicationService.retryJob(1L, "admin")).thenReturn(detail);
        when(syncResponseAssembler.toResponse(detail)).thenReturn(buildDetailResponse("RETRY_BATCH"));

        mockMvc.perform(post("/api/v1/sync/jobs/{id}/retry", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batch.batchNo").value("RETRY_BATCH"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldListJobsSuccessfully() throws Exception {
        allow("/api/v1/sync/jobs", "GET");
        SyncJob job = mock(SyncJob.class);
        when(syncApplicationService.listJobs()).thenReturn(List.of(job));
        when(syncResponseAssembler.toJobResponse(job)).thenReturn(new SyncJobResponse(
            1L, "BATCH_001", "LDAP_RECONCILE_USER", "USER", "SUCCESS", "{}", "{}", null, "admin", 0
        ));

        mockMvc.perform(get("/api/v1/sync/jobs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].batchNo").value("BATCH_001"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldGetBatchDetailSuccessfully() throws Exception {
        allow("/api/v1/sync/batches/BATCH_001", "GET");
        SyncBatchDetail detail = mock(SyncBatchDetail.class);
        when(syncApplicationService.getBatchDetail("BATCH_001")).thenReturn(detail);
        when(syncResponseAssembler.toResponse(detail)).thenReturn(buildDetailResponse("BATCH_001"));

        mockMvc.perform(get("/api/v1/sync/batches/{batchNo}", "BATCH_001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batch.batchNo").value("BATCH_001"));
    }

    private SyncBatchDetailResponse buildDetailResponse(String batchNo) {
        return new SyncBatchDetailResponse(
            new SyncBatchResponse(1L, batchNo, "LDAP_RECONCILE", "SYSTEM", "MANUAL", "SUCCESS", null, null, null, "admin", null),
            List.of(),
            List.of()
        );
    }
}
