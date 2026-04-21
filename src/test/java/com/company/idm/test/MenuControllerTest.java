package com.company.idm.test;

import com.company.idm.application.rbac.CreateMenuCommand;
import com.company.idm.application.rbac.DeleteMenuCommand;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.rbac.UpdateMenuCommand;
import com.company.idm.common.enums.MenuType;
import com.company.idm.domain.rbac.Menu;
import com.company.idm.interfaces.menu.MenuController;
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

@WebMvcTest(MenuController.class)
@ControllerMvcSlice
@Import(MenuController.class)
class MenuControllerTest extends AbstractControllerMvcTest {

    @MockBean
    private RbacApplicationService rbacApplicationService;

    @Test
    @WithMockUser(username = "admin")
    void shouldGetMenuDetailSuccessfully() throws Exception {
        allow("/api/v1/menus/1", "GET");
        when(rbacApplicationService.getMenu(1L)).thenReturn(buildMenu(1L, "SYSTEM", 0L));

        mockMvc.perform(get("/api/v1/menus/{id}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menuCode").value("SYSTEM"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldCreateMenuSuccessfully() throws Exception {
        allow("/api/v1/menus", "POST");
        when(rbacApplicationService.createMenu(argThat((CreateMenuCommand command) ->
            "SYSTEM".equals(command.menuCode())
        ), argThat("admin"::equals))).thenReturn(buildMenu(1L, "SYSTEM", 0L));

        mockMvc.perform(post("/api/v1/menus")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "menuCode": "SYSTEM",
                      "menuName": "系统管理",
                      "parentId": 0,
                      "menuType": "CATALOG",
                      "path": "/system",
                      "component": "system/index",
                      "icon": "setting",
                      "sortNo": 1,
                      "minPermissionLevel": 1,
                      "remark": "系统管理"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menuCode").value("SYSTEM"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldUpdateMenuSuccessfully() throws Exception {
        allow("/api/v1/menus/1", "PUT");
        when(rbacApplicationService.updateMenu(argThat((UpdateMenuCommand command) ->
            command.menuId().equals(1L)
        ), argThat("admin"::equals))).thenReturn(buildMenu(1L, "SYSTEM", 0L));

        mockMvc.perform(put("/api/v1/menus/{id}", 1L)
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "menuName": "系统管理",
                      "parentId": 0,
                      "menuType": "CATALOG",
                      "path": "/system",
                      "component": "system/index",
                      "icon": "setting",
                      "sortNo": 1,
                      "minPermissionLevel": 1,
                      "remark": "系统管理"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menuCode").value("SYSTEM"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldDeleteMenuSuccessfully() throws Exception {
        allow("/api/v1/menus/1", "DELETE");

        mockMvc.perform(delete("/api/v1/menus/{id}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnMenuTreeSuccessfully() throws Exception {
        allow("/api/v1/menus/tree", "GET");
        when(rbacApplicationService.listAllMenus()).thenReturn(List.of(
            buildMenu(1L, "SYSTEM", 0L),
            buildMenu(2L, "USER", 1L)
        ));

        mockMvc.perform(get("/api/v1/menus/tree"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].menuCode").value("SYSTEM"))
            .andExpect(jsonPath("$.data[0].children[0].menuCode").value("USER"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnCurrentUserMenuTreeSuccessfully() throws Exception {
        when(rbacApplicationService.listCurrentUserMenus("admin")).thenReturn(List.of(
            buildMenu(1L, "SYSTEM", 0L),
            buildMenu(2L, "USER", 1L)
        ));

        mockMvc.perform(get("/api/v1/menus/self/tree"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].menuCode").value("SYSTEM"))
            .andExpect(jsonPath("$.data[0].children[0].menuCode").value("USER"));
    }

    private Menu buildMenu(Long id, String menuCode, Long parentId) {
        return Menu.builder()
            .id(id)
            .menuCode(menuCode)
            .menuName(menuCode + "菜单")
            .parentId(parentId)
            .menuType(parentId == 0 ? MenuType.CATALOG : MenuType.MENU)
            .path("/" + menuCode.toLowerCase())
            .component(menuCode.toLowerCase() + "/index")
            .icon("setting")
            .sortNo(1)
            .minPermissionLevel(1)
            .remark("remark")
            .build();
    }
}
