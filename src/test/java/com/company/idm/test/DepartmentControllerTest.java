package com.company.idm.test;

import com.company.idm.application.department.CreateDepartmentCommand;
import com.company.idm.application.department.DeleteDepartmentCommand;
import com.company.idm.application.department.DepartmentApplicationService;
import com.company.idm.application.department.UpdateDepartmentCommand;
import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.application.sync.SyncBatchDetail;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.domain.department.Department;
import com.company.idm.interfaces.department.DepartmentController;
import com.company.idm.interfaces.sync.SyncBatchDetailResponse;
import com.company.idm.interfaces.sync.SyncBatchResponse;
import com.company.idm.interfaces.sync.SyncResponseAssembler;
import com.company.idm.test.support.AbstractControllerMvcTest;
import com.company.idm.test.support.ControllerMvcSlice;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DepartmentController.class)
@ControllerMvcSlice
@Import(DepartmentController.class)
class DepartmentControllerTest extends AbstractControllerMvcTest {

    @MockBean
    private DepartmentApplicationService departmentApplicationService;

    @MockBean
    private SyncApplicationService syncApplicationService;

    @MockBean
    private SyncResponseAssembler syncResponseAssembler;

    @Test
    void shouldReturnUnauthorizedWhenReadDepartmentTreeWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/departments/tree"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnDepartmentTreeSuccessfully() throws Exception {
        allow("/api/v1/departments/tree", "GET");
        when(departmentApplicationService.listDepartments()).thenReturn(List.of(
            buildDepartment("D001", "研发中心", null),
            buildDepartment("D002", "后端组", "D001")
        ));

        mockMvc.perform(get("/api/v1/departments/tree"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].deptCode").value("D001"))
            .andExpect(jsonPath("$.data[0].children[0].deptCode").value("D002"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldGetDepartmentDetailSuccessfully() throws Exception {
        allow("/api/v1/departments/D001", "GET");
        when(departmentApplicationService.getDepartment("D001")).thenReturn(buildDepartment("D001", "研发中心", null));

        mockMvc.perform(get("/api/v1/departments/{deptCode}", "D001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deptCode").value("D001"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldCreateDepartmentSuccessfully() throws Exception {
        allow("/api/v1/departments", "POST");
        when(departmentApplicationService.createDepartment(argThat((CreateDepartmentCommand command) ->
            "D100".equals(command.deptCode()) && "平台研发部".equals(command.deptName())
        ), argThat("admin"::equals))).thenReturn(buildDepartment("D100", "平台研发部", null));

        mockMvc.perform(post("/api/v1/departments")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "deptCode": "D100",
                      "deptName": "平台研发部",
                      "parentDeptCode": null,
                      "externalId": "ou_root_001"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deptCode").value("D100"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnBadRequestWhenCreateDepartmentRequestInvalid() throws Exception {
        allow("/api/v1/departments", "POST");

        mockMvc.perform(post("/api/v1/departments")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "deptCode": "",
                      "deptName": "",
                      "parentDeptCode": null,
                      "externalId": "ou_root_001"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldUpdateDepartmentSuccessfully() throws Exception {
        allow("/api/v1/departments/D100", "PUT");
        when(departmentApplicationService.updateDepartment(argThat((UpdateDepartmentCommand command) ->
            "D100".equals(command.deptCode()) && "平台研发部".equals(command.deptName())
        ), argThat("admin"::equals))).thenReturn(buildDepartment("D100", "平台研发部", null));

        mockMvc.perform(put("/api/v1/departments/{deptCode}", "D100")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "deptName": "平台研发部",
                      "parentDeptCode": null,
                      "status": 1
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deptCode").value("D100"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldDeleteDepartmentSuccessfully() throws Exception {
        allow("/api/v1/departments/D100", "DELETE");

        mockMvc.perform(delete("/api/v1/departments/{deptCode}", "D100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldSyncDepartmentsFromFeishuSuccessfully() throws Exception {
        allow("/api/v1/departments/sync/feishu", "POST");
        SyncBatchDetail detail = mock(SyncBatchDetail.class);
        when(syncApplicationService.executeFeishuDepartmentSync("admin", SyncTriggerMode.MANUAL)).thenReturn(detail);
        when(syncResponseAssembler.toResponse(detail)).thenReturn(buildSyncBatchDetailResponse("DEPT_SYNC_BATCH"));

        mockMvc.perform(post("/api/v1/departments/sync/feishu")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "remark": "manual-sync"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batch.batchNo").value("DEPT_SYNC_BATCH"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldImportDepartmentsFromFeishuFileSuccessfully() throws Exception {
        allow("/api/v1/departments/import/feishu-file", "POST");
        SyncBatchDetail detail = mock(SyncBatchDetail.class);
        when(syncApplicationService.executeFeishuDepartmentFileImport("departments/demo.json", false, "manual-file-import", "admin", SyncTriggerMode.MANUAL))
            .thenReturn(detail);
        when(syncResponseAssembler.toResponse(detail)).thenReturn(buildSyncBatchDetailResponse("DEPT_FILE_BATCH"));

        mockMvc.perform(post("/api/v1/departments/import/feishu-file")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "documentPath": "departments/demo.json",
                      "remark": "manual-file-import",
                      "forceFullSync": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batch.batchNo").value("DEPT_FILE_BATCH"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldSyncDepartmentToLdapSuccessfully() throws Exception {
        allow("/api/v1/departments/D100/sync-ldap", "POST");
        when(departmentApplicationService.syncDepartmentToLdap("D100", "admin")).thenReturn(buildDepartment("D100", "平台研发部", null));

        mockMvc.perform(post("/api/v1/departments/{deptCode}/sync-ldap", "D100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.deptCode").value("D100"));
    }

    private Department buildDepartment(String deptCode, String deptName, String parentDeptCode) {
        return Department.builder()
            .id(1L)
            .deptCode(deptCode)
            .deptName(deptName)
            .parentDeptCode(parentDeptCode)
            .ancestorPath(parentDeptCode == null ? "/" + deptCode : "/D001/" + deptCode)
            .deptLevel(parentDeptCode == null ? 1 : 2)
            .sourceType(SourceType.MANUAL)
            .externalId("ou_" + deptCode)
            .ldapDn("cn=" + deptCode + "_" + deptName + ",ou=groups,dc=corp,dc=local")
            .status(1)
            .build();
    }

    private SyncBatchDetailResponse buildSyncBatchDetailResponse(String batchNo) {
        return new SyncBatchDetailResponse(
            new SyncBatchResponse(1L, batchNo, "FEISHU_IMPORT", "FEISHU", "MANUAL", "SUCCESS", null, null, null, "admin", null),
            List.of(),
            List.of()
        );
    }
}
