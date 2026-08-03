package com.company.idm.test.support;

import com.company.idm.infrastructure.casbin.CasbinAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

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

}
