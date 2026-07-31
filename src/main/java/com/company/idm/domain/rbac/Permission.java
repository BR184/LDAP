package com.company.idm.domain.rbac;

import com.company.idm.common.enums.PermissionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 权限领域实体，描述菜单、按钮或接口权限。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    private Long id;
    private String permissionCode;
    private String permissionName;
    private PermissionType permissionType;
    private String resourcePath;
    private String action;
    private Long parentId;
    private Integer sortNo;
    private Integer status;
    private String remark;
}

