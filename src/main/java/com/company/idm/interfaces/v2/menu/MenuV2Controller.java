package com.company.idm.interfaces.v2.menu;

import com.company.idm.application.rbac.CreateMenuCommand;
import com.company.idm.application.rbac.DeleteMenuCommand;
import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.rbac.UpdateMenuCommand;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.domain.rbac.Menu;
import com.company.idm.interfaces.menu.CreateMenuRequest;
import com.company.idm.interfaces.menu.MenuResponse;
import com.company.idm.interfaces.menu.MenuTreeNodeResponse;
import com.company.idm.interfaces.menu.UpdateMenuRequest;
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
 * V2 菜单管理接口。
 */
@RestController
@RequestMapping("/api/v2/menus")
@RequiredArgsConstructor
public class MenuV2Controller {

    private final RbacApplicationService rbacApplicationService;

    @GetMapping("/{id}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'MENU_DETAIL')")
    public ApiResponseV2<MenuResponse> detail(@PathVariable Long id) {
        return ApiResponseV2.ok(toResponse(rbacApplicationService.getMenu(id)));
    }

    @PostMapping
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'MENU_CREATE')")
    public ApiResponseV2<MenuResponse> create(
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
        return ApiResponseV2.ok(toResponse(menu));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'MENU_UPDATE')")
    public ApiResponseV2<MenuResponse> update(
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
        return ApiResponseV2.ok(toResponse(menu));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'MENU_DELETE')")
    public ApiResponseV2<Void> delete(
        @PathVariable Long id,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        rbacApplicationService.deleteMenu(new DeleteMenuCommand(id, username));
        return ApiResponseV2.ok();
    }

    @GetMapping("/tree")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'MENU_TREE')")
    public ApiResponseV2<List<MenuTreeNodeResponse>> tree() {
        return ApiResponseV2.ok(buildTree(rbacApplicationService.listAllMenus()));
    }

    @GetMapping("/self/tree")
    @PreAuthorize("@credentialAccessService.isSession(authentication)")
    public ApiResponseV2<List<MenuTreeNodeResponse>> selfTree(
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponseV2.ok(buildTree(rbacApplicationService.listCurrentUserMenus(username)));
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
