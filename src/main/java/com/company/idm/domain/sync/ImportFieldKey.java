package com.company.idm.domain.sync;

/**
 * Stable, versioned identifiers for fields owned by the personnel import source.
 */
public enum ImportFieldKey {
    DEPARTMENT_NAME("部门名称"),
    DEPARTMENT_PARENT("上级部门"),
    DEPARTMENT_PATH("组织层级"),
    DEPARTMENT_LEVEL("部门层级"),
    DEPARTMENT_STATUS("部门状态"),
    USER_REAL_NAME("姓名"),
    USER_EMAIL("工作邮箱"),
    USER_MOBILE("手机号"),
    USER_EMPLOYEE_NO("工号"),
    USER_MAIN_DEPARTMENT("主部门"),
    USER_JOB_TITLE("岗位"),
    USER_DIRECT_LEADER("直属上级"),
    USER_LEADER_REFERENCE("直属上级关联"),
    USER_ACCOUNT_STATUS("账号状态"),
    USER_PART_TIME_DEPARTMENTS("兼职部门"),
    USER_EMPLOYMENT_STATUS("在职状态");

    private final String label;

    ImportFieldKey(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ImportFieldKey fromStorage(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ImportFieldKey.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
