package com.company.idm.test;

import com.company.idm.application.rbac.BindRoleMenusCommand;
import com.company.idm.application.rbac.CreateRoleCommand;
import com.company.idm.application.rbac.DeleteRoleCommand;
import com.company.idm.application.rbac.GrantRolePermissionsCommand;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.rbac.UpdateRoleCommand;
import com.company.idm.application.rbac.UpdateRoleStatusCommand;
import com.company.idm.domain.rbac.Role;
import com.company.idm.interfaces.role.RoleController;
import com.company.idm.test.support.AbstractControllerMvcTest;
import com.company.idm.test.support.ControllerMvcSlice;
import java.util.List;
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

@WebMvcTest(RoleController.class)
@ControllerMvcSlice
@Import(RoleController.class)
class RoleControllerTest extends AbstractControllerMvcTest {

    @MockBean
    private RbacApplicationService rbacApplicationService;

    @Test
    void shouldReturnUnauthorizedWhenListRolesWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/roles"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldListRolesSuccessfully() throws Exception {
        allow("/api/v1/roles", "GET");
        when(rbacApplicationService.listRoles()).thenReturn(List.of(buildRole(1L, "ADMIN")));

        mockMvc.perform(get("/api/v1/roles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].roleCode").value("ADMIN"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldGetRoleDetailSuccessfully() throws Exception {
        allow("/api/v1/roles/1", "GET");
        when(rbacApplicationService.getRole(1L)).thenReturn(buildRole(1L, "ADMIN"));

        mockMvc.perform(get("/api/v1/roles/{id}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleCode").value("ADMIN"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldGetRoleMenuIdsSuccessfully() throws Exception {
        allow("/api/v1/roles/1/menus", "GET");
        when(rbacApplicationService.listRoleMenuIds(1L)).thenReturn(List.of(1L, 2L, 8L));

        mockMvc.perform(get("/api/v1/roles/{id}/menus", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0]").value(1))
            .andExpect(jsonPath("$.data[2]").value(8));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldGetRolePermissionIdsSuccessfully() throws Exception {
        allow("/api/v1/roles/1/permissions", "GET");
        when(rbacApplicationService.listRolePermissionIds(1L)).thenReturn(List.of(3L, 4L));

        mockMvc.perform(get("/api/v1/roles/{id}/permissions", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0]").value(3))
            .andExpect(jsonPath("$.data[1]").value(4));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldCreateRoleSuccessfully() throws Exception {
        allow("/api/v1/roles", "POST");
        when(rbacApplicationService.createRole(argThat((CreateRoleCommand command) ->
            "ADMIN".equals(command.roleCode())
        ), argThat("admin"::equals))).thenReturn(buildRole(1L, "ADMIN"));

        mockMvc.perform(post("/api/v1/roles")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "roleCode": "ADMIN",
                      "roleName": "管理员",
                      "permissionLevel": 1,
                      "remark": "系统管理员"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleCode").value("ADMIN"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnBadRequestWhenCreateRoleRequestInvalid() throws Exception {
        allow("/api/v1/roles", "POST");

        mockMvc.perform(post("/api/v1/roles")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "roleCode": "",
                      "roleName": "",
                      "permissionLevel": 0,
                      "remark": "系统管理员"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldUpdateRoleSuccessfully() throws Exception {
        allow("/api/v1/roles/1", "PUT");
        when(rbacApplicationService.updateRole(argThat((UpdateRoleCommand command) ->
            command.roleId().equals(1L)
        ), argThat("admin"::equals))).thenReturn(buildRole(1L, "ADMIN"));

        mockMvc.perform(put("/api/v1/roles/{id}", 1L)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "roleName": "管理员",
                      "permissionLevel": 1,
                      "remark": "系统管理员"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roleCode").value("ADMIN"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldUpdateRoleStatusSuccessfully() throws Exception {
        allow("/api/v1/roles/1/status", "PUT");

        mockMvc.perform(put("/api/v1/roles/{id}/status", 1L)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "status": 0
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldDeleteRoleSuccessfully() throws Exception {
        allow("/api/v1/roles/1", "DELETE");

        mockMvc.perform(delete("/api/v1/roles/{id}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldGrantPermissionsSuccessfully() throws Exception {
        allow("/api/v1/roles/1/permissions", "PUT");

        mockMvc.perform(put("/api/v1/roles/{id}/permissions", 1L)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "permissionIds": [1, 2]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldBindMenusSuccessfully() throws Exception {
        allow("/api/v1/roles/1/menus", "PUT");

        mockMvc.perform(put("/api/v1/roles/{id}/menus", 1L)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "menuIds": [1, 2]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    private Role buildRole(Long id, String roleCode) {
        return Role.builder()
            .id(id)
            .roleCode(roleCode)
            .roleName("管理员")
            .permissionLevel(1)
            .builtIn(1)
            .status(1)
            .remark("系统管理员")
            .build();
    }
}
