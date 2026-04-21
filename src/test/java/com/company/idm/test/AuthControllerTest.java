package com.company.idm.test;

import com.company.idm.application.auth.AuthApplicationService;
import com.company.idm.application.auth.LoginResult;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.domain.user.User;
import com.company.idm.interfaces.auth.AuthController;
import com.company.idm.test.support.AbstractControllerMvcTest;
import com.company.idm.test.support.ControllerMvcSlice;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@ControllerMvcSlice
@Import(AuthController.class)
class AuthControllerTest extends AbstractControllerMvcTest {

    @MockBean
    private AuthApplicationService authApplicationService;

    @Test
    void shouldLoginSuccessfully() throws Exception {
        when(authApplicationService.login(argThat(command ->
            "admin".equals(command.username()) && "admin123456".equals(command.password())
        ))).thenReturn(new LoginResult(
            1L,
            "admin",
            Set.of("ADMIN"),
            "token-value",
            Instant.parse("2026-04-21T12:00:00Z")
        ));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "username": "admin",
                      "password": "admin123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.userId").value(1))
            .andExpect(jsonPath("$.data.username").value("admin"))
            .andExpect(jsonPath("$.data.accessToken").value("token-value"));
    }

    @Test
    void shouldReturnBadRequestWhenLoginRequestInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "username": "",
                      "password": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
    }

    @Test
    void shouldReturnUnauthorizedWhenGetCurrentUserWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnForbiddenWhenGetCurrentUserWithoutPermission() throws Exception {
        deny("/api/v1/auth/me", "GET");

        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldLoadCurrentUserSuccessfully() throws Exception {
        allow("/api/v1/auth/me", "GET");
        when(authApplicationService.loadProfile("admin")).thenReturn(User.builder()
            .id(1L)
            .username("admin")
            .realName("系统管理员")
            .email("admin@corp.local")
            .mobile("13800000000")
            .deptCode("D001")
            .status(UserStatus.ENABLED)
            .sourceType(SourceType.MANUAL)
            .roleCodes(Set.of("ADMIN"))
            .build());

        mockMvc.perform(get("/api/v1/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("admin"))
            .andExpect(jsonPath("$.data.deptCode").value("D001"))
            .andExpect(jsonPath("$.data.roleCodes[0]").value("ADMIN"));
    }
}
