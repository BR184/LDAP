package com.company.idm.infrastructure.config;

import com.company.idm.infrastructure.sql.SqlLogInterceptor;
import com.company.idm.infrastructure.sql.SqlLogProperties;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 提供 MyBatis-Plus 的基础配置入口。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public Interceptor sqlLogInterceptor(SqlLogProperties sqlLogProperties) {
        return new SqlLogInterceptor(sqlLogProperties);
    }
}

