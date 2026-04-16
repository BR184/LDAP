package com.company.idm.test;

import com.company.idm.boot.IdmBootApplication;
import com.company.idm.common.log.TraceIdConstants;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证认证、用户管理与权限链路的集成测试。
 */
@SpringBootTest(classes = IdmBootApplication.class)
@AutoConfigureMockMvc
class PrototypeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldLoginAndReadCurrentUserProfile() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("admin"))
            .andExpect(jsonPath("$.data.roleCodes[0]").value("SUPER_ADMIN"));
    }

    @Test
    void shouldExposeTraceIdHeader() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "admin",
                      "password": "admin123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getHeader(TraceIdConstants.HEADER_NAME)).isNotBlank());
    }

    @Test
    void shouldReturnUnauthorizedWhenTokenMissing() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void shouldReturnValidationErrorWhenLoginRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
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
    void shouldReturnBizErrorWhenCreatingDuplicateUser() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "admin",
                      "realName": "重复管理员",
                      "email": "duplicate-admin@corp.local",
                      "mobile": "13700000000",
                      "employeeNo": "E9999",
                      "deptCode": "D001",
                      "initialPassword": "Password@123",
                      "roleIds": [1]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("USER_DUPLICATE"));
    }

    @Test
    void shouldCreateUserAndDisableUser() throws Exception {
        String token = loginAsAdmin();

        MvcResult createResult = mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
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
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.username").value("zhangsan"))
            .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long userId = created.path("data").path("id").asLong();
        assertThat(userId).isPositive();

        mockMvc.perform(put("/api/v1/users/{id}/status", userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "statusCode": 0
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        MvcResult listResult = mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode users = objectMapper.readTree(listResult.getResponse().getContentAsString()).path("data");
        JsonNode targetUser = null;
        for (JsonNode user : users) {
            if ("zhangsan".equals(user.path("username").asText())) {
                targetUser = user;
                break;
            }
        }
        assertThat(targetUser).isNotNull();
        assertThat(targetUser.path("status").asInt()).isEqualTo(0);
    }

    @Test
    void shouldReturnForbiddenWhenUserHasNoPermission() throws Exception {
        String adminToken = loginAsAdmin();
        String suffix = String.valueOf(System.nanoTime());
        String roleCode = "GUEST_" + suffix;
        String username = "guest" + suffix;

        MvcResult createRoleResult = mockMvc.perform(post("/api/v1/roles")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "roleCode": "%s",
                      "roleName": "访客角色",
                      "remark": "仅用于异常处理测试"
                    }
                    """.formatted(roleCode)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();

        long roleId = objectMapper.readTree(createRoleResult.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asLong();
        assertThat(roleId).isPositive();

        mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "realName": "访客用户",
                      "email": "%s@corp.local",
                      "mobile": "13600000000",
                      "employeeNo": "E%s",
                      "deptCode": "D001",
                      "initialPassword": "Password@123",
                      "roleIds": [%d]
                    }
                    """.formatted(username, username, suffix, roleId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        String guestToken = login(username, "Password@123");

        mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + guestToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    void shouldUpdateDeleteChangeAndResetPasswordForUserManagement() throws Exception {
        String adminToken = loginAsAdmin();
        String username = "worker" + System.nanoTime();

        MvcResult createResult = mockMvc.perform(post("/api/v1/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "realName": "员工用户",
                      "email": "%s@corp.local",
                      "mobile": "13500000000",
                      "employeeNo": "E%s",
                      "deptCode": "D001",
                      "initialPassword": "123456",
                      "roleIds": [1]
                    }
                    """.formatted(username, username, System.nanoTime())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();

        long userId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .path("data")
            .path("id")
            .asLong();

        mockMvc.perform(put("/api/v1/users/{id}", userId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "realName": "员工用户-更新",
                      "email": "%s-updated@corp.local",
                      "mobile": "13511111111",
                      "employeeNo": "E20002",
                      "deptCode": "D001"
                    }
                    """.formatted(username)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.realName").value("员工用户-更新"))
            .andExpect(jsonPath("$.data.mobile").value("13511111111"));

        String userToken = login(username, "123456");

        mockMvc.perform(put("/api/v1/users/me/password")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "oldPassword": "123456",
                      "newPassword": "654321",
                      "confirmPassword": "654321"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "password": "123456"
                    }
                    """.formatted(username)))
            .andExpect(status().isUnauthorized());

        String newToken = login(username, "654321");
        assertThat(newToken).isNotBlank();

        mockMvc.perform(put("/api/v1/users/{id}/password/reset", userId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.resetPassword").value("123456"));

        String resetToken = login(username, "123456");
        assertThat(resetToken).isNotBlank();

        mockMvc.perform(delete("/api/v1/users/{id}", userId)
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        MvcResult listResult = mockMvc.perform(get("/api/v1/users")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode users = objectMapper.readTree(listResult.getResponse().getContentAsString()).path("data");
        boolean exists = false;
        for (JsonNode user : users) {
            if (username.equals(user.path("username").asText())) {
                exists = true;
                break;
            }
        }
        assertThat(exists).isFalse();

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "password": "123456"
                    }
                    """.formatted(username)))
            .andExpect(status().isUnauthorized());
    }

    private String loginAsAdmin() throws Exception {
        return login("admin", "admin123456");
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "%s",
                      "password": "%s"
                    }
                    """.formatted(username, password)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();

        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        return jsonNode.path("data").path("accessToken").asText();
    }
}
