package com.company.idm.infrastructure.sql;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 拦截 MyBatis 执行过程并输出统一格式的 SQL 日志。
 */
@Intercepts({
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, org.apache.ibatis.cache.CacheKey.class, BoundSql.class})
})
public class SqlLogInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlLogInterceptor.class);

    private final SqlLogProperties properties;
    private final SqlLogFormatter formatter;

    public SqlLogInterceptor(SqlLogProperties properties) {
        this.properties = properties;
        this.formatter = new SqlLogFormatter(properties);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!properties.isEnabled()) {
            return invocation.proceed();
        }

        long start = System.nanoTime();
        Throwable throwable = null;
        Object result = null;

        try {
            result = invocation.proceed();
            return result;
        } catch (Throwable ex) {
            throwable = ex;
            throw ex;
        } finally {
            writeSqlLog(invocation, result, throwable, System.nanoTime() - start);
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    private void writeSqlLog(Invocation invocation, Object result, Throwable throwable, long costNanos) {
        Object[] args = invocation.getArgs();
        MappedStatement mappedStatement = (MappedStatement) args[0];
        Object parameterObject = args.length > 1 ? args[1] : null;
        BoundSql boundSql = args.length == 6 ? (BoundSql) args[5] : mappedStatement.getBoundSql(parameterObject);

        long costMs = costNanos / 1_000_000;
        String sql = formatter.normalizeSql(boundSql.getSql());
        String params = formatter.formatParameters(extractParameters(mappedStatement.getConfiguration(), boundSql, parameterObject));
        String resultSummary = formatter.formatResultSummary(result);
        String message = "sqlId={}, commandType={}, costMs={}, sql={}, params={}, {}";

        if (throwable != null) {
            log.error(message, mappedStatement.getId(), mappedStatement.getSqlCommandType(), costMs, sql, params, "failed", throwable);
            return;
        }

        if (costMs >= properties.getSlowSqlThresholdMs()) {
            log.warn("[SLOW_SQL] " + message,
                mappedStatement.getId(), mappedStatement.getSqlCommandType(), costMs, sql, params, resultSummary);
            return;
        }

        log.info("[SQL] " + message,
            mappedStatement.getId(), mappedStatement.getSqlCommandType(), costMs, sql, params, resultSummary);
    }

    private Map<String, Object> extractParameters(Configuration configuration, BoundSql boundSql, Object parameterObject) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (boundSql.getParameterMappings() == null || boundSql.getParameterMappings().isEmpty()) {
            return parameters;
        }

        MetaObject metaObject = parameterObject == null ? SystemMetaObject.NULL_META_OBJECT : configuration.newMetaObject(parameterObject);

        for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
            String propertyName = parameterMapping.getProperty();
            if (propertyName == null || parameters.containsKey(propertyName)) {
                continue;
            }
            Object value;
            if (boundSql.hasAdditionalParameter(propertyName)) {
                value = boundSql.getAdditionalParameter(propertyName);
            } else if (parameterObject == null) {
                value = null;
            } else if (configuration.getTypeHandlerRegistry().hasTypeHandler(parameterObject.getClass())) {
                value = parameterObject;
            } else if (metaObject != SystemMetaObject.NULL_META_OBJECT && metaObject.hasGetter(propertyName)) {
                value = metaObject.getValue(propertyName);
            } else {
                PropertyTokenizer propertyTokenizer = new PropertyTokenizer(propertyName);
                if (boundSql.hasAdditionalParameter(propertyTokenizer.getName())) {
                    value = boundSql.getAdditionalParameter(propertyTokenizer.getName());
                } else {
                    value = null;
                }
            }
            parameters.put(propertyName, value);
        }
        return parameters;
    }
}

