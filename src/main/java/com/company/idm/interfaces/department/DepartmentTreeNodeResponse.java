package com.company.idm.interfaces.department;

import java.util.ArrayList;
import java.util.List;

/**
 * 封装部门树节点响应结构。
 */
public record DepartmentTreeNodeResponse(
    String deptCode,
    String deptName,
    String parentDeptCode,
    String ancestorPath,
    Integer deptLevel,
    Integer status,
    List<DepartmentTreeNodeResponse> children
) {

    public static DepartmentTreeNodeResponse create(
        String deptCode,
        String deptName,
        String parentDeptCode,
        String ancestorPath,
        Integer deptLevel,
        Integer status
    ) {
        return new DepartmentTreeNodeResponse(
            deptCode,
            deptName,
            parentDeptCode,
            ancestorPath,
            deptLevel,
            status,
            new ArrayList<>()
        );
    }
}
