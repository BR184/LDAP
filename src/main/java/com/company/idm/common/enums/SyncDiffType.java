package com.company.idm.common.enums;

/**
 * 定义同步差异类型枚举。
 */
public enum SyncDiffType {
    MISSING_IN_MYSQL,
    MISSING_IN_LDAP,
    FIELD_MISMATCH,
    RELATION_MISMATCH,
    STATUS_MISMATCH,
    DN_MISMATCH
}
