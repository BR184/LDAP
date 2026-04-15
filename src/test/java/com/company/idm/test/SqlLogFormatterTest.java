package com.company.idm.test;

import com.company.idm.infrastructure.sql.SqlLogFormatter;
import com.company.idm.infrastructure.sql.SqlLogProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 SQL 日志格式化、脱敏和截断规则的单元测试。
 */
class SqlLogFormatterTest {

    @Test
    void shouldNormalizeSqlAndMaskSensitiveParameters() {
        SqlLogProperties properties = new SqlLogProperties();
        properties.setEnabled(true);
        properties.setMaxSqlLength(100);
        properties.setMaxParameterLength(10);
        SqlLogFormatter formatter = new SqlLogFormatter(properties);

        String sql = formatter.normalizeSql("SELECT  *  \n FROM sys_user \t WHERE username = ? AND password = ?");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("username", "administrator");
        params.put("password", "Password@123");

        String formattedParams = formatter.formatParameters(params);

        assertThat(sql).isEqualTo("SELECT * FROM sys_user WHERE username = ? AND password = ?");
        assertThat(formattedParams).contains("username=\"administra...\"");
        assertThat(formattedParams).contains("password=\"***\"");
    }

    @Test
    void shouldSummarizeCollectionResult() {
        SqlLogProperties properties = new SqlLogProperties();
        SqlLogFormatter formatter = new SqlLogFormatter(properties);

        assertThat(formatter.formatResultSummary(java.util.List.of("a", "b", "c"))).isEqualTo("rows=3");
        assertThat(formatter.formatResultSummary(2)).isEqualTo("affectedRows=2");
    }
}

