package com.company.idm.interfaces.menu;

import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.domain.rbac.Menu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供菜单树查询接口。
 */
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class MenuController {

    private final RbacApplicationService rbacApplicationService;

    @GetMapping("/tree")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/menus/tree', 'GET')")
    public ApiResponse<List<MenuTreeNodeResponse>> tree() {
        return ApiResponse.success(buildTree(rbacApplicationService.listAllMenus()));
    }

    @GetMapping("/self/tree")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<MenuTreeNodeResponse>> selfTree(
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        return ApiResponse.success(buildTree(rbacApplicationService.listCurrentUserMenus(username)));
    }

    private List<MenuTreeNodeResponse> buildTree(List<Menu> menus) {
        Map<Long, MenuTreeNodeResponse> index = new LinkedHashMap<>();
        for (Menu menu : menus) {
            index.put(menu.getId(), MenuTreeNodeResponse.create(
                menu.getId(),
                menu.getMenuCode(),
                menu.getMenuName(),
                menu.getMenuType().name(),
                menu.getPath(),
                menu.getComponent(),
                menu.getIcon(),
                menu.getParentId(),
                menu.getSortNo()
            ));
        }
        List<MenuTreeNodeResponse> roots = new ArrayList<>();
        for (MenuTreeNodeResponse node : index.values()) {
            if (node.parentId() == null || node.parentId() == 0) {
                roots.add(node);
                continue;
            }
            MenuTreeNodeResponse parent = index.get(node.parentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return roots;
    }
}
