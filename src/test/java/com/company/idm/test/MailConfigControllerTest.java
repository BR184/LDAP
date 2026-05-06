package com.company.idm.test;

import com.company.idm.application.mail.MailConfigApplicationService;
import com.company.idm.application.mail.MailConfigTestResult;
import com.company.idm.application.mail.MailServerConfigDetail;
import com.company.idm.interfaces.system.MailConfigController;
import com.company.idm.test.support.AbstractControllerMvcTest;
import com.company.idm.test.support.ControllerMvcSlice;
import java.time.LocalDateTime;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MailConfigController.class)
@ControllerMvcSlice
@Import(MailConfigController.class)
class MailConfigControllerTest extends AbstractControllerMvcTest {

    @MockBean
    private MailConfigApplicationService mailConfigApplicationService;

    @Test
    void shouldReturnUnauthorizedWhenGetMailConfigWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/system/mail-config"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldGetMailConfigSuccessfully() throws Exception {
        allow("/api/v1/system/mail-config", "GET");
        when(mailConfigApplicationService.getCurrentConfig()).thenReturn(buildDetail());

        mockMvc.perform(get("/api/v1/system/mail-config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.sendMode").value("SMTP"))
            .andExpect(jsonPath("$.data.host").value("10.0.100.49"))
            .andExpect(jsonPath("$.data.port").value(587))
            .andExpect(jsonPath("$.data.fromAddress").value("huayun@crowncad.com"))
            .andExpect(jsonPath("$.data.passwordConfigured").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldSaveMailConfigSuccessfully() throws Exception {
        allow("/api/v1/system/mail-config", "PUT");
        when(mailConfigApplicationService.saveConfig(argThat(command ->
            "SMTP".equals(command.sendMode())
                && "STARTTLS".equals(command.secureMode())
                && "10.0.100.49".equals(command.host())
                && command.port() == 587
                && Boolean.TRUE.equals(command.authRequired())
                && "huayun@crowncad.com".equals(command.fromAddress())
                && "CrownCAD".equals(command.fromName())
                && "huayun@crowncad.com".equals(command.username())
                && "mail-password".equals(command.password())
                && "admin".equals(command.operator())
        ))).thenReturn(buildDetail());

        mockMvc.perform(put("/api/v1/system/mail-config")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "sendMode": "SMTP",
                      "secureMode": "STARTTLS",
                      "host": "10.0.100.49",
                      "port": 587,
                      "fromAddress": "huayun@crowncad.com",
                      "fromName": "CrownCAD",
                      "authRequired": true,
                      "username": "huayun@crowncad.com",
                      "password": "mail-password",
                      "enabled": true,
                      "remark": "内网邮件配置"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fromAddress").value("huayun@crowncad.com"))
            .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldTestMailConfigSuccessfully() throws Exception {
        allow("/api/v1/system/mail-config/test", "POST");
        when(mailConfigApplicationService.testConfig(argThat(command ->
            "admin".equals(command.operator())
                && "receiver@crowncad.com".equals(command.testToAddress())
                && "SMTP".equals(command.sendMode())
        ))).thenReturn(new MailConfigTestResult(true, "测试邮件发送成功"));

        mockMvc.perform(post("/api/v1/system/mail-config/test")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "sendMode": "SMTP",
                      "secureMode": "STARTTLS",
                      "host": "10.0.100.49",
                      "port": 587,
                      "fromAddress": "huayun@crowncad.com",
                      "fromName": "CrownCAD",
                      "authRequired": true,
                      "username": "huayun@crowncad.com",
                      "password": "mail-password",
                      "testToAddress": "receiver@crowncad.com"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(true))
            .andExpect(jsonPath("$.data.message").value("测试邮件发送成功"));
    }

    private MailServerConfigDetail buildDetail() {
        return new MailServerConfigDetail(
            1L,
            "SMTP",
            "STARTTLS",
            "10.0.100.49",
            587,
            "huayun@crowncad.com",
            "CrownCAD",
            true,
            "huayun@crowncad.com",
            true,
            true,
            "内网邮件配置",
            true,
            LocalDateTime.of(2026, 5, 6, 10, 0, 0),
            "测试通过"
        );
    }
}
