package com.company.idm.interfaces.user;

/**
 * A display-ready department reference while preserving its stable code for edit submissions.
 */
public record DepartmentReferenceResponse(String deptCode, String deptName, String departmentPath) {
}
