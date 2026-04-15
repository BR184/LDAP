package com.company.idm.interfaces.role;

/**
 * 封装角色接口的响应数据。
 */
public record RoleResponse(Long id, String roleCode, String roleName, Integer status, String remark) {
}

