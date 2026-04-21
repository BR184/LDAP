package com.company.idm.test.support;

import com.company.idm.infrastructure.casbin.CasbinAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public abstract class AbstractControllerMvcTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockBean(name = "casbinAccessService")
    protected CasbinAccessService casbinAccessService;

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected void allow(String obj, String act) {
        when(casbinAccessService.check(any(Authentication.class), eq(obj), eq(act))).thenReturn(true);
    }

    protected void deny(String obj, String act) {
        when(casbinAccessService.check(any(Authentication.class), eq(obj), eq(act))).thenReturn(false);
    }
}
