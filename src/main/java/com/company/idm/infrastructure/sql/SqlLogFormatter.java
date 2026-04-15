package com.company.idm.infrastructure.sql;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 负责格式化 SQL 日志内容并处理敏感参数脱敏。
 */
public class SqlLogFormatter {

    private final SqlLogProperties properties;

    public SqlLogFormatter(SqlLogProperties properties) {
        this.properties = properties;
    }

    public String normalizeSql(String sql) {
        if (sql == null) {
            return "";
        }
        String normalized = sql.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= properties.getMaxSqlLength()) {
            return normalized;
        }
        return normalized.substring(0, properties.getMaxSqlLength()) + "...";
    }

    public String formatParameters(Map<String, Object> parameters) {
        if (!properties.isShowParameters() || parameters == null || parameters.isEmpty()) {
            return "[]";
        }
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        parameters.forEach((name, value) -> joiner.add(name + "=" + formatValue(name, value)));
        return joiner.toString();
    }

    public String formatResultSummary(Object result) {
        if (result == null) {
            return "result=null";
        }
        if (result instanceof Number number) {
            return "affectedRows=" + number;
        }
        if (result instanceof Collection<?> collection) {
            return "rows=" + collection.size();
        }
        return "resultType=" + result.getClass().getSimpleName();
    }

    public String formatValue(String parameterName, Object value) {
        if (isSensitive(parameterName)) {
            return "\"***\"";
        }
        if (value == null) {
            return "null";
        }
        if (value instanceof byte[] bytes) {
            return "\"<bytes:" + bytes.length + ">\"";
        }
        if (value.getClass().isArray()) {
            return formatArray(parameterName, value);
        }
        if (value instanceof Collection<?> collection) {
            return formatCollection(parameterName, collection);
        }
        if (value instanceof Map<?, ?> map) {
            return "\"<map:size=" + map.size() + ">\"";
        }
        return formatScalar(value);
    }

    private boolean isSensitive(String parameterName) {
        if (parameterName == null || parameterName.isBlank()) {
            return false;
        }
        String normalizedName = parameterName.toLowerCase(Locale.ROOT);
        for (String keyword : properties.getMaskKeywords()) {
            if (normalizedName.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String formatArray(String parameterName, Object array) {
        int length = Array.getLength(array);
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        int limit = Math.min(length, properties.getMaxCollectionLength());
        for (int i = 0; i < limit; i++) {
            joiner.add(formatValue(parameterName, Array.get(array, i)));
        }
        if (length > limit) {
            joiner.add("...");
        }
        return joiner.toString();
    }

    private String formatCollection(String parameterName, Collection<?> collection) {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        Iterator<?> iterator = collection.iterator();
        int count = 0;
        while (iterator.hasNext() && count < properties.getMaxCollectionLength()) {
            joiner.add(formatValue(parameterName, iterator.next()));
            count++;
        }
        if (collection.size() > properties.getMaxCollectionLength()) {
            joiner.add("...");
        }
        return joiner.toString();
    }

    private String formatScalar(Object value) {
        String text = String.valueOf(value);
        if (text.length() > properties.getMaxParameterLength()) {
            text = text.substring(0, properties.getMaxParameterLength()) + "...";
        }
        return "\"" + text + "\"";
    }
}

