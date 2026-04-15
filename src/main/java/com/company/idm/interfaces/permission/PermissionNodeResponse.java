package com.company.idm.interfaces.permission;

import java.util.ArrayList;
import java.util.List;

/**
 * 封装权限树节点的响应结构。
 */
public record PermissionNodeResponse(
    Long id,
    String permissionCode,
    String permissionName,
    String permissionType,
    String resourcePath,
    String action,
    Long parentId,
    List<PermissionNodeResponse> children
) {

    public PermissionNodeResponse withChildren(List<PermissionNodeResponse> childNodes) {
        return new PermissionNodeResponse(id, permissionCode, permissionName, permissionType, resourcePath, action, parentId, childNodes);
    }

    public static PermissionNodeResponse create(
        Long id,
        String permissionCode,
        String permissionName,
        String permissionType,
        String resourcePath,
        String action,
        Long parentId
    ) {
        return new PermissionNodeResponse(id, permissionCode, permissionName, permissionType, resourcePath, action, parentId, new ArrayList<>());
    }
}

