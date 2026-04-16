package com.company.idm.interfaces.role;

/**
 * 封装角色接口的响应数据。
 */
public record RoleResponse(
    Long id,
    String roleCode,
    String roleName,
    Integer permissionLevel,
    Integer builtIn,
    Integer status,
    String remark
) {
}


