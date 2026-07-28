package com.company.idm.application.department;

public record DepartmentDisplay(String departmentName, String departmentPath) {

    public static DepartmentDisplay empty() {
        return new DepartmentDisplay(null, null);
    }
}
