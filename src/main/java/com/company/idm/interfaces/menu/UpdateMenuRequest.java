package com.company.idm.interfaces.menu;

import com.company.idm.common.enums.MenuType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 封装更新菜单接口的请求参数。
 */
public record UpdateMenuRequest(
    @NotBlank(message = "菜单名称不能为空") String menuName,
    @Min(value = 0, message = "父菜单ID不能小于0") Long parentId,
    @NotNull(message = "菜单类型不能为空") MenuType menuType,
    @NotBlank(message = "菜单路由不能为空") String path,
    String component,
    String icon,
    @Min(value = 0, message = "排序值不能小于0") Integer sortNo,
    String remark
) {
}
