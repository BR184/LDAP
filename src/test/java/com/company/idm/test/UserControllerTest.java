package com.company.idm.test;

import com.company.idm.application.rbac.AssignUserRolesCommand;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.sync.SyncApplicationService;
import com.company.idm.application.sync.SyncBatchDetail;
import com.company.idm.application.user.ChangePasswordCommand;
import com.company.idm.application.user.CreateUserCommand;
import com.company.idm.application.user.DeleteUserCommand;
import com.company.idm.application.user.ResetPasswordCommand;
import com.company.idm.application.user.UpdateUserCommand;
import com.company.idm.application.user.UpdateUserStatusCommand;
import com.company.idm.application.user.UserApplicationService;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.SyncTriggerMode;
import com.company.idm.common.enums.UserStatus;
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
import static org.mockito.Mockito.mock;
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
    void shouldReturnForbiddenWhenListUsersWithoutPermission() throws Exception {
        deny("/api/v1/users", "GET");

        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldListUsersSuccessfully() throws Exception {
        allow("/api/v1/users", "GET");
        when(userApplicationService.listUsers()).thenReturn(List.of(buildUser(1L, "admin", UserStatus.ENABLED)));

        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(1))
            .andExpect(jsonPath("$.data[0].username").value("admin"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldGetUserDetailSuccessfully() throws Exception {
        allow("/api/v1/users/1", "GET");
        when(userApplicationService.getUser(1L)).thenReturn(buildUser(1L, "admin", UserStatus.ENABLED));

        mockMvc.perform(get("/api/v1/users/{id}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldCreateUserSuccessfully() throws Exception {
        allow("/api/v1/users", "POST");
        when(userApplicationService.createUser(argThat((CreateUserCommand command) ->
            "zhangsan".equals(command.username())
                && "admin".equals(command.operator())
                && List.of(1L).equals(command.roleIds())
        ))).thenReturn(buildUser(2L, "zhangsan", UserStatus.ENABLED));

        mockMvc.perform(post("/api/v1/users")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "username": "zhangsan",
                      "realName": "张三",
                      "email": "zhangsan@corp.local",
                      "mobile": "13900000000",
                      "employeeNo": "E10001",
                      "deptCode": "D001",
                      "initialPassword": "Password@123",
                      "roleIds": [1]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(2))
            .andExpect(jsonPath("$.data.username").value("zhangsan"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnBadRequestWhenCreateUserRequestInvalid() throws Exception {
        allow("/api/v1/users", "POST");

        mockMvc.perform(post("/api/v1/users")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "username": "",
                      "realName": "",
                      "email": "bad-email",
                      "mobile": "13900000000",
                      "employeeNo": "E10001",
                      "deptCode": "D001",
                      "initialPassword": "",
                      "roleIds": []
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldUpdateUserSuccessfully() throws Exception {
        allow("/api/v1/users/2", "PUT");
        when(userApplicationService.updateUser(argThat((UpdateUserCommand command) ->
            command.userId().equals(2L) && "admin".equals(command.operator())
        ))).thenReturn(buildUser(2L, "zhangsan", UserStatus.ENABLED));

        mockMvc.perform(put("/api/v1/users/{id}", 2L)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "realName": "张三",
                      "email": "zhangsan@corp.local",
                      "mobile": "13900000000",
                      "employeeNo": "E10001",
                      "deptCode": "D001"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(2));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldUpdateUserStatusSuccessfully() throws Exception {
        allow("/api/v1/users/2/status", "PUT");

        mockMvc.perform(put("/api/v1/users/{id}/status", 2L)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "statusCode": 0
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldDeleteUserSuccessfully() throws Exception {
        allow("/api/v1/users/2", "DELETE");

        mockMvc.perform(delete("/api/v1/users/{id}", 2L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldChangePasswordSuccessfully() throws Exception {
        mockMvc.perform(put("/api/v1/users/me/password")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "oldPassword": "old123",
                      "newPassword": "new123",
                      "confirmPassword": "new123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldResetPasswordSuccessfully() throws Exception {
        allow("/api/v1/users/2/password/reset", "PUT");
        when(userApplicationService.resetPassword(argThat((ResetPasswordCommand command) ->
            command.userId().equals(2L) && "admin".equals(command.operator())
        ))).thenReturn("123456");

        mockMvc.perform(put("/api/v1/users/{id}/password/reset", 2L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.resetPassword").value("123456"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldAssignRolesSuccessfully() throws Exception {
        allow("/api/v1/users/2/roles", "PUT");

        mockMvc.perform(put("/api/v1/users/{id}/roles", 2L)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "roleIds": [1, 2]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldSyncFeishuUsersSuccessfully() throws Exception {
        allow("/api/v1/users/sync/feishu", "POST");
        SyncBatchDetail detail = mock(SyncBatchDetail.class);
        when(syncApplicationService.executeFeishuUserSync("admin", SyncTriggerMode.MANUAL)).thenReturn(detail);
        when(syncResponseAssembler.toResponse(detail)).thenReturn(buildSyncBatchDetailResponse("BATCH_USER_SYNC"));

        mockMvc.perform(post("/api/v1/users/sync/feishu")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "remark": "manual-sync"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batch.batchNo").value("BATCH_USER_SYNC"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldImportFeishuUsersFromFileSuccessfully() throws Exception {
        allow("/api/v1/users/import/feishu-file", "POST");
        SyncBatchDetail detail = mock(SyncBatchDetail.class);
        when(syncApplicationService.executeFeishuUserFileImport("users/demo.json", false, "manual-file-import", "admin", SyncTriggerMode.MANUAL))
            .thenReturn(detail);
        when(syncResponseAssembler.toResponse(detail)).thenReturn(buildSyncBatchDetailResponse("BATCH_USER_FILE"));

        mockMvc.perform(post("/api/v1/users/import/feishu-file")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "documentPath": "users/demo.json",
                      "remark": "manual-file-import",
                      "forceFullSync": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.batch.batchNo").value("BATCH_USER_FILE"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldSyncSingleUserToLdapSuccessfully() throws Exception {
        allow("/api/v1/users/2/sync-ldap", "POST");
        when(userApplicationService.syncUserToLdap(2L, "admin")).thenReturn(buildUser(2L, "zhangsan", UserStatus.ENABLED));

        mockMvc.perform(post("/api/v1/users/{id}/sync-ldap", 2L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(2))
            .andExpect(jsonPath("$.data.username").value("zhangsan"));
    }

    private User buildUser(Long id, String username, UserStatus status) {
        return User.builder()
            .id(id)
            .username(username)
            .realName("测试用户")
            .email(username + "@corp.local")
            .mobile("13900000000")
            .employeeNo("E10001")
            .deptCode("D001")
            .status(status)
            .sourceType(SourceType.MANUAL)
            .ldapDn("uid=" + username + ",ou=people,dc=corp,dc=local")
            .roleCodes(Set.of("ADMIN"))
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
