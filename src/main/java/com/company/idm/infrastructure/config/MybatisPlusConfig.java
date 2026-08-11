package com.company.idm.infrastructure.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
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

    /**
     * MyBatis-Plus 分页拦截器：为 V2 服务端分页查询提供 limit/offset 与自动 count。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}

