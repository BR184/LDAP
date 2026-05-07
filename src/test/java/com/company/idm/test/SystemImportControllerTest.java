package com.company.idm.test;

import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.application.sync.SyncBatchDetail;
import com.company.idm.application.sync.feishu.FeishuImportUploadService;
import com.company.idm.common.enums.ImportMode;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.interfaces.sync.SyncBatchDetailResponse;
import com.company.idm.interfaces.sync.SyncBatchResponse;
import com.company.idm.interfaces.sync.SyncResponseAssembler;
import com.company.idm.interfaces.system.SystemImportController;
import com.company.idm.test.support.AbstractControllerMvcTest;
import com.company.idm.test.support.ControllerMvcSlice;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemImportController.class)
@ControllerMvcSlice
@Import(SystemImportController.class)
class SystemImportControllerTest extends AbstractControllerMvcTest {

    @MockBean
    private SyncApplicationService syncApplicationService;

    @MockBean
    private SyncResponseAssembler syncResponseAssembler;

    @MockBean
    private FeishuImportUploadService feishuImportUploadService;

    @Test
    void shouldReturnUnauthorizedWhenImportWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/system/imports/feishu/full")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "documentPath": "imports/full-demo.xlsx",
                      "remark": "manual-full-import",
                      "importMode": "SUPPLEMENT"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldExecuteFeishuFullImportSuccessfully() throws Exception {
        allow("/api/v1/system/imports/feishu/full", "POST");
        SyncBatchDetail detail = mock(SyncBatchDetail.class);
        when(syncApplicationService.executeFeishuFullFileImport(
            "imports/full-demo.xlsx",
            ImportMode.ALIGN,
            "manual-full-import",
            "admin",
            SyncTriggerMode.MANUAL
        )).thenReturn(detail);
        when(syncResponseAssembler.toResponse(detail)).thenReturn(buildSyncBatchDetailResponse("BATCH_FULL_FILE"));

        mockMvc.perform(post("/api/v1/system/imports/feishu/full")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "documentPath": "imports/full-demo.xlsx",
                      "remark": "manual-full-import",
                      "importMode": "ALIGN"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batch.batchNo").value("BATCH_FULL_FILE"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldExecuteFeishuFullImportByUploadSuccessfully() throws Exception {
        allow("/api/v1/system/imports/feishu/full", "POST");
        SyncBatchDetail detail = mock(SyncBatchDetail.class);
        when(feishuImportUploadService.store(org.mockito.ArgumentMatchers.any())).thenReturn("docs/feishu-import/uploaded.xlsx");
        when(syncApplicationService.executeFeishuFullFileImport(
            "docs/feishu-import/uploaded.xlsx",
            ImportMode.SUPPLEMENT,
            "drag-import",
            "admin",
            SyncTriggerMode.MANUAL
        )).thenReturn(detail);
        when(syncResponseAssembler.toResponse(detail)).thenReturn(buildSyncBatchDetailResponse("BATCH_UPLOAD_FILE"));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "通讯录-导出改.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "demo".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/system/imports/feishu/full/upload")
                .file(file)
                .param("importMode", "SUPPLEMENT")
                .param("remark", "drag-import"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batch.batchNo").value("BATCH_UPLOAD_FILE"));
    }

    private SyncBatchDetailResponse buildSyncBatchDetailResponse(String batchNo) {
        return new SyncBatchDetailResponse(
            new SyncBatchResponse(1L, batchNo, "FEISHU_IMPORT", "FEISHU", "MANUAL", "SUCCESS", null, "ALIGN", null, "admin", null),
            java.util.List.of(),
            java.util.List.of()
        );
    }
}
