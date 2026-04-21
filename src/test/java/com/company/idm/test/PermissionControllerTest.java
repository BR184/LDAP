package com.company.idm.test;

import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.common.enums.PermissionType;
import com.company.idm.domain.rbac.Permission;
import com.company.idm.interfaces.permission.PermissionController;
import com.company.idm.test.support.AbstractControllerMvcTest;
import com.company.idm.test.support.ControllerMvcSlice;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PermissionController.class)
@ControllerMvcSlice
@Import(PermissionController.class)
class PermissionControllerTest extends AbstractControllerMvcTest {

    @MockBean
    private RbacApplicationService rbacApplicationService;

    @Test
    void shouldReturnUnauthorizedWhenReadPermissionTreeWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/permissions/tree"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnPermissionTreeSuccessfully() throws Exception {
        allow("/api/v1/permissions/tree", "GET");
        when(rbacApplicationService.listPermissions()).thenReturn(List.of(
            Permission.builder()
                .id(1L)
                .permissionCode("SYSTEM")
                .permissionName("系统管理")
                .permissionType(PermissionType.MENU)
                .resourcePath("/system")
                .action("GET")
                .parentId(0L)
                .build(),
            Permission.builder()
                .id(2L)
                .permissionCode("USER_READ")
                .permissionName("用户查询")
                .permissionType(PermissionType.API)
                .resourcePath("/api/v1/users")
                .action("GET")
                .parentId(1L)
                .build()
        ));

        mockMvc.perform(get("/api/v1/permissions/tree"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].permissionCode").value("SYSTEM"))
            .andExpect(jsonPath("$.data[0].children[0].permissionCode").value("USER_READ"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnForbiddenWhenReadPermissionTreeWithoutPermission() throws Exception {
        deny("/api/v1/permissions/tree", "GET");

        mockMvc.perform(get("/api/v1/permissions/tree"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }
}
