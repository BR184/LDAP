package com.company.idm.infrastructure.casbin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.model.Model;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class CasbinConfiguration {

    @Bean
    public Enforcer enforcer() throws IOException {
        String modelText = new String(
            new ClassPathResource("casbin/rbac-model.conf").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
        );
        Model model = new Model();
        model.loadModelFromText(modelText);
        return new Enforcer(model);
    }
}

