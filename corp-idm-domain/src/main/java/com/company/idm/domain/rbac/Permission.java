package com.company.idm.domain.rbac;

import com.company.idm.common.enums.PermissionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
}

