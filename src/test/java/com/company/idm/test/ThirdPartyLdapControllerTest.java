package com.company.idm.test;

import com.company.idm.application.ldap.ThirdPartyLdapCheckStatus;
import com.company.idm.application.ldap.ThirdPartyLdapFrameworkDetail;
import com.company.idm.application.ldap.ThirdPartyLdapIntegrationApplicationService;
import com.company.idm.application.ldap.ThirdPartyLdapPrecheckItem;
import com.company.idm.application.ldap.ThirdPartyLdapPrecheckReport;
import com.company.idm.application.ldap.ThirdPartyLdapTemplateDetail;
import com.company.idm.interfaces.ldap.ThirdPartyLdapController;
import com.company.idm.test.support.AbstractControllerMvcTest;
import com.company.idm.test.support.ControllerMvcSlice;
import java.util.List;
import java.util.Map;
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

@WebMvcTest(ThirdPartyLdapController.class)
@ControllerMvcSlice
@Import(ThirdPartyLdapController.class)
class ThirdPartyLdapControllerTest extends AbstractControllerMvcTest {

    @MockBean
    private ThirdPartyLdapIntegrationApplicationService thirdPartyLdapIntegrationApplicationService;

    @Test
    void shouldReturnUnauthorizedWhenReadFrameworkWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/ldap/framework"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReadFrameworkSuccessfully() throws Exception {
        allow("/api/v1/ldap/framework", "GET");
        when(thirdPartyLdapIntegrationApplicationService.getFramework()).thenReturn(new ThirdPartyLdapFrameworkDetail(
            "spring",
            "dc=corp,dc=local",
            "ou=people,dc=corp,dc=local",
            "ou=groups,dc=corp,dc=local",
            "uid",
            "(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))",
            "LOCAL_ONLY",
            List.of("gitlab", "jenkins")
        ));

        mockMvc.perform(get("/api/v1/ldap/framework"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mode").value("spring"))
            .andExpect(jsonPath("$.data.loginAttr").value("uid"))
            .andExpect(jsonPath("$.data.supportedSystems[0]").value("gitlab"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReadTemplateSuccessfully() throws Exception {
        allow("/api/v1/ldap/templates/gitlab", "GET");
        when(thirdPartyLdapIntegrationApplicationService.getTemplate("gitlab")).thenReturn(new ThirdPartyLdapTemplateDetail(
            "gitlab",
            "GitLab",
            "docs/templates/gitlab-ldap-template.md",
            Map.of(
                "host", "ldap.corp.local",
                "port", "389",
                "user_filter", "(&(objectClass=inetOrgPerson)(employeeType=ENABLED))"
            ),
            Map.of("uid", "uid", "name", "cn", "email", "mail"),
            List.of("note-1", "note-2")
        ));

        mockMvc.perform(get("/api/v1/ldap/templates/{systemCode}", "gitlab"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.systemCode").value("gitlab"))
            .andExpect(jsonPath("$.data.settings.user_filter").value("(&(objectClass=inetOrgPerson)(employeeType=ENABLED))"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturnBadRequestWhenPrecheckRequestInvalid() throws Exception {
        allow("/api/v1/ldap/precheck", "POST");

        mockMvc.perform(post("/api/v1/ldap/precheck")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "systemCode": "gitlab",
                      "enabledUsername": "",
                      "disabledUsername": "lisi"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PARAM_INVALID"));
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldRunPrecheckSuccessfully() throws Exception {
        allow("/api/v1/ldap/precheck", "POST");
        when(thirdPartyLdapIntegrationApplicationService.precheck(argThat(command ->
            "gitlab".equals(command.systemCode())
                && "zhangsan".equals(command.enabledUsername())
                && "lisi".equals(command.disabledUsername())
        ))).thenReturn(new ThirdPartyLdapPrecheckReport(
            "gitlab",
            "spring",
            "(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))",
            ThirdPartyLdapCheckStatus.PASS,
            List.of(
                new ThirdPartyLdapPrecheckItem("LOGIN_ATTR_FIXED", "登录字段固定", ThirdPartyLdapCheckStatus.PASS, "ok"),
                new ThirdPartyLdapPrecheckItem("DISABLED_USER_FILTER_BLOCK", "禁用用户过滤拦截", ThirdPartyLdapCheckStatus.PASS, "ok")
            )
        ));

        mockMvc.perform(post("/api/v1/ldap/precheck")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "systemCode": "gitlab",
                      "enabledUsername": "zhangsan",
                      "disabledUsername": "lisi"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.overallStatus").value("PASS"))
            .andExpect(jsonPath("$.data.items[1].code").value("DISABLED_USER_FILTER_BLOCK"));
    }
}
