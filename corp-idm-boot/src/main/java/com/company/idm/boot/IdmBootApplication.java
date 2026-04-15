package com.company.idm.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.company.idm")
@ConfigurationPropertiesScan(basePackages = "com.company.idm")
@MapperScan("com.company.idm.infrastructure.persistence.mapper")
public class IdmBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdmBootApplication.class, args);
    }
}

