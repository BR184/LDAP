package com.company.idm.test;

import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.application.sync.SyncBatchDetail;
import com.company.idm.application.user.AdminResetPasswordCommand;
import com.company.idm.application.user.BatchDeleteUsersResult;
import com.company.idm.application.user.CreateUserCommand;
import com.company.idm.application.user.PasswordResetApplicationService;
import com.company.idm.application.user.UpdateUserCommand;
import com.company.idm.application.user.UserApplicationService;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.SyncBatchType;
import com.company.idm.common.enums.SyncJobType;
import com.company.idm.common.enums.SyncRunStatus;
import com.company.idm.common.enums.SyncSourceType;
import com.company.idm.common.enums.SyncTargetType;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.domain.sync.SyncBatch;
import com.company.idm.domain.sync.SyncJob;
import com.company.idm.domain.user.User;
import com.company.idm.interfaces.sync.SyncBatchDetailResponse;
import com.company.idm.interfaces.sync.SyncBatchResponse;
import com.company.idm.interfaces.sync.SyncResponseAssembler;
import com.company.idm.interfaces.user.UserController;
import com.company.idm.test.support.AbstractControllerMvcTest;
import com.company.idm.test.support.ControllerMvcSlice;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@ControllerMvcSlice
@Import(UserController.class)
class UserControllerTest extends AbstractControllerMvcTest {

    @MockBean
    private UserApplicationService userApplicationService;

    @MockBean
    private PasswordResetApplicationService passwordResetApplicationService;

    @MockBean
    private RbacApplicationService rbacApplicationService;

    @MockBean
    private SyncApplicationService syncApplicationService;

    @MockBean
    private SyncResponseAssembler syncResponseAssembler;

    @Test
    void shouldReturnUnauthorizedWhenListUsersWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldCreateUserSuccessfully() throws Exception {
        allow("/api/v1/users", "POST");
        when(userApplicationService.createUser(argThat((CreateUserCommand command) ->
            "张三".equals(command.realName())
                && "zhangsan@corp.local".equals(command.email())
                && "zhangsan@crowncad.com".equals(command.intranetEmail())
                && "E10001".equals(command.employeeNo())
                && List.of(1L).equals(command.roleIds())
        ))).thenReturn(buildUser(2L, "zhangsane10001", UserStatus.ENABLED, "E10001"));

        mockMvc.perform(post("/api/v1/users")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "realName": "张三",
                      "email": "zhangsan@corp.local",
                      "intranetEmail": "zhangsan@crowncad.com",
                      "mobile": "13900000000",
                      "employeeNo": "E10001",
                      "deptCode": "D001",
                      "initialPassword": "123456",
                      "roleIds": [1]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("zhangsane10001"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldUpdateUserSuccessfully() throws Exception {
        allow("/api/v1/users/2", "PUT");
        when(userApplicationService.updateUser(argThat((UpdateUserCommand command) ->
            command.userId().equals(2L)
                && "admin".equals(command.operator())
                && "E10002".equals(command.employeeNo())
                && "zhangsan@corp.local".equals(command.email())
                && "zhangsan@crowncad.com".equals(command.intranetEmail())
        ))).thenReturn(buildUser(2L, "zhangsane10001", UserStatus.ENABLED, "E10002"));

        mockMvc.perform(put("/api/v1/users/{id}", 2L)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "realName": "张三",
                      "email": "zhangsan@corp.local",
                      "intranetEmail": "zhangsan@crowncad.com",
                      "mobile": "13900000000",
                      "employeeNo": "E10002",
                      "deptCode": "D001"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.employeeNo").value("E10002"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldAllowEmptyWorkEmailOnUpdate() throws Exception {
        allow("/api/v1/users/2", "PUT");
        when(userApplicationService.updateUser(argThat((UpdateUserCommand command) ->
            command.userId().equals(2L)
                && "admin".equals(command.operator())
                && "E10002".equals(command.employeeNo())
                && (command.email() == null || command.email().isBlank())
                && "zhangsan@crowncad.com".equals(command.intranetEmail())
        ))).thenReturn(buildUser(2L, "zhangsane10001", UserStatus.ENABLED, "E10002"));

        mockMvc.perform(put("/api/v1/users/{id}", 2L)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "realName": "张三",
                      "email": "",
                      "intranetEmail": "zhangsan@crowncad.com",
                      "mobile": "13900000000",
                      "employeeNo": "E10002",
                      "deptCode": "D001"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.employeeNo").value("E10002"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldBatchDeleteUsersSuccessfully() throws Exception {
        allow("/api/v1/users/batch-delete", "POST");
        when(userApplicationService.batchDeleteUsers(argThat(command ->
            command.operator().equals("admin") && command.userIds().equals(List.of(2L, 3L))
        ))).thenReturn(new BatchDeleteUsersResult(2, 2));

        mockMvc.perform(post("/api/v1/users/batch-delete")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "userIds": [2, 3]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.deletedCount").value(2));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldBatchDeleteUsersByQuerySuccessfully() throws Exception {
        allow("/api/v1/users/batch-delete", "POST");
        when(userApplicationService.batchDeleteUsers(argThat(command ->
            command.operator().equals("admin")
                && command.userIds().isEmpty()
                && "1001".equals(command.usernameKeyword())
                && "产品研发一部/四组".equals(command.deptNameKeyword())
                && Integer.valueOf(1).equals(command.statusCode())
        ))).thenReturn(new BatchDeleteUsersResult(12, 12));

        mockMvc.perform(post("/api/v1/users/batch-delete")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "userIds": [],
                      "usernameKeyword": "1001",
                      "deptNameKeyword": "产品研发一部/四组",
                      "statusCode": 1
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalCount").value(12))
            .andExpect(jsonPath("$.data.deletedCount").value(12));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldResetPasswordSuccessfully() throws Exception {
        allow("/api/v1/users/2/password/reset", "PUT");
        org.mockito.Mockito.doNothing().when(passwordResetApplicationService).adminResetPassword(argThat((AdminResetPasswordCommand command) ->
            command.userId().equals(2L) && "admin".equals(command.operator())
        ));

        mockMvc.perform(put("/api/v1/users/{id}/password/reset", 2L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnBadRequestWhenUserFileImportBatchFails() throws Exception {
        allow("/api/v1/users/import/feishu-file", "POST");
        SyncBatchDetail detail = new SyncBatchDetail(
            SyncBatch.builder()
                .id(1L)
                .batchNo("BATCH_USER_FILE_FAIL")
                .batchType(SyncBatchType.FEISHU_IMPORT)
                .sourceType(SyncSourceType.FEISHU)
                .triggerMode(SyncTriggerMode.MANUAL)
                .status(SyncRunStatus.FAIL)
                .fileName("users/demo.json")
                .operator("admin")
                .build(),
            List.of(
                SyncJob.builder()
                    .id(1L)
                    .batchNo("BATCH_USER_FILE_FAIL")
                    .jobType(SyncJobType.FEISHU_USER_IMPORT)
                    .targetType(SyncTargetType.USER)
                    .status(SyncRunStatus.FAIL)
                    .errorMessage("请先更新部门文件后再导入用户文件")
                    .operator("admin")
                    .retryCount(0)
                    .build()
            ),
            List.of()
        );
        when(syncApplicationService.executeFeishuUserFileImport("users/demo.json", false, "manual-file-import", "admin", SyncTriggerMode.MANUAL))
            .thenReturn(detail);

        mockMvc.perform(post("/api/v1/users/import/feishu-file")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "documentPath": "users/demo.json",
                      "remark": "manual-file-import",
                      "forceFullSync": false
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("USER_FILE_IMPORT_FAILED"))
            .andExpect(jsonPath("$.message").value("请先更新部门文件后再导入用户文件"));
    }

    private User buildUser(Long id, String username, UserStatus status, String employeeNo) {
        return User.builder()
            .id(id)
            .username(username)
            .realName("测试用户")
            .email(username + "@corp.local")
            .mobile("13900000000")
            .employeeNo(employeeNo)
            .deptName("研发中心")
            .deptCode("D001")
            .status(status)
            .sourceType(SourceType.MANUAL)
            .ldapDn("uid=" + username + ",ou=people,dc=corp,dc=local")
            .roleCodes(Set.of("ADMIN"))
            .build();
    }

    @SuppressWarnings("unused")
    private SyncBatchDetailResponse buildSyncBatchDetailResponse(String batchNo) {
        return new SyncBatchDetailResponse(
            new SyncBatchResponse(1L, batchNo, "FEISHU_IMPORT", "FEISHU", "MANUAL", "SUCCESS", null, null, null, "admin", null),
            List.of(),
            List.of()
        );
    }
}
