package com.company.idm.interfaces.menu;

import com.company.idm.application.rbac.CreateMenuCommand;
import com.company.idm.application.rbac.DeleteMenuCommand;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.rbac.UpdateMenuCommand;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.domain.rbac.Menu;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鎻愪緵鑿滃崟鏍戞煡璇㈡帴鍙ｃ€? */
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class MenuController {

    private final RbacApplicationService rbacApplicationService;

    @GetMapping("/{id}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/menus/' + #id, 'GET')")
    public ApiResponse<MenuResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(toResponse(rbacApplicationService.getMenu(id)));
    }

    @PostMapping
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/menus', 'POST')")
    public ApiResponse<MenuResponse> create(
        @Valid @RequestBody CreateMenuRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        Menu menu = rbacApplicationService.createMenu(new CreateMenuCommand(
            request.menuCode(),
            request.menuName(),
            request.parentId(),
            request.menuType(),
            request.path(),
            request.component(),
            request.icon(),
            request.sortNo(),
            request.remark()
        ), username);
        return ApiResponse.success(toResponse(menu));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/menus/' + #id, 'PUT')")
    public ApiResponse<MenuResponse> update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateMenuRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        Menu menu = rbacApplicationService.updateMenu(new UpdateMenuCommand(
            id,
            request.menuName(),
            request.parentId(),
            request.menuType(),
            request.path(),
            request.component(),
            request.icon(),
            request.sortNo(),
            request.remark()
        ), username);
        return ApiResponse.success(toResponse(menu));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/menus/' + #id, 'DELETE')")
    public ApiResponse<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        rbacApplicationService.deleteMenu(new DeleteMenuCommand(id, username));
        return ApiResponse.success();
    }

    @GetMapping("/tree")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/menus/tree', 'GET')")
    public ApiResponse<List<MenuTreeNodeResponse>> tree() {
        return ApiResponse.success(buildTree(rbacApplicationService.listAllMenus()));
    }

    @GetMapping("/self/tree")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<MenuTreeNodeResponse>> selfTree(
        @AuthenticationPrincipal(expression = "userId") String username
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

    private MenuResponse toResponse(Menu menu) {
        return new MenuResponse(
            menu.getId(),
            menu.getMenuCode(),
            menu.getMenuName(),
            menu.getParentId(),
            menu.getMenuType().name(),
            menu.getPath(),
            menu.getComponent(),
            menu.getIcon(),
            menu.getSortNo(),
            menu.getRemark()
        );
    }
}

