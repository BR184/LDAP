package com.company.idm.interfaces.v2.permission;

import com.company.idm.application.rbac.RbacApplicationService;
import com.company.idm.application.rbac.RolePermissionBundle;
import com.company.idm.application.rbac.RolePermissionBundleCatalog;
import com.company.idm.application.rbac.RolePermissionBundleTier;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.domain.rbac.Permission;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 权限查询接口。
 */
@RestController
@RequestMapping("/api/v2/permissions")
@RequiredArgsConstructor
public class PermissionV2Controller {

    private final RbacApplicationService rbacApplicationService;
    private final RolePermissionBundleCatalog rolePermissionBundleCatalog;

    @GetMapping("/bundles")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'PERMISSION_TREE')")
    public ApiResponseV2<List<RolePermissionBundleResponse>> bundles() {
        return ApiResponseV2.ok(rolePermissionBundleCatalog.bundles().stream()
            .map(this::toResponse)
            .toList());
    }

    @GetMapping("/tree")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'PERMISSION_TREE')")
    public ApiResponseV2<List<PermissionNodeResponse>> tree() {
        List<Permission> permissions = rbacApplicationService.listPermissions();
        Map<Long, PermissionNodeResponse> index = new LinkedHashMap<>();
        for (Permission permission : permissions) {
            index.put(permission.getId(), PermissionNodeResponse.create(
                permission.getId(),
                permission.getPermissionCode(),
                permission.getPermissionName(),
                permission.getPermissionType().name(),
                permission.getResourcePath(),
                permission.getAction(),
                permission.getParentId()
            ));
        }
        List<PermissionNodeResponse> roots = new ArrayList<>();
        for (PermissionNodeResponse node : index.values()) {
            if (node.parentId() == null || node.parentId() == 0) {
                roots.add(node);
                continue;
            }
            PermissionNodeResponse parent = index.get(node.parentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children().add(node);
            }
        }
        return ApiResponseV2.ok(roots);
    }

    private RolePermissionBundleResponse toResponse(RolePermissionBundle bundle) {
        return new RolePermissionBundleResponse(
            bundle.id(), bundle.name(), bundle.sort(), bundle.permissionIds(), bundle.permissionCodes(),
            bundle.categoryId(), bundle.categoryName(), bundle.categoryDescription(), bundle.categorySort(), bundle.tier()
        );
    }

    public record RolePermissionBundleResponse(
        String id,
        String name,
        int sort,
        List<Long> permissionIds,
        List<String> permissionCodes,
        String categoryId,
        String categoryName,
        String categoryDescription,
        int categorySort,
        RolePermissionBundleTier tier
    ) {
    }

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
}
