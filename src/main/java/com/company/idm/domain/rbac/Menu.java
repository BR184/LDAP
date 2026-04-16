package com.company.idm.domain.rbac;

import com.company.idm.common.enums.MenuType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 菜单领域实体，描述前端可渲染的菜单节点。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Menu {

    private Long id;
    private String menuCode;
    private String menuName;
    private Long parentId;
    private MenuType menuType;
    private String path;
    private String component;
    private String icon;
    private Integer sortNo;
    private Integer status;
    private Integer visible;
    private Integer minPermissionLevel;
    private String remark;
}

