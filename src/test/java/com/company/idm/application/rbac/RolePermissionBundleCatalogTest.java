package com.company.idm.application.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.idm.common.enums.PermissionType;
import com.company.idm.domain.rbac.Permission;
import java.util.List;
import org.junit.jupiter.api.Test;

class RolePermissionBundleCatalogTest {

    @Test
    void resolvesConfiguredPermissionCodesToStablePermissionIds() {
        RolePermissionBundleCatalog catalog = new RolePermissionBundleCatalog(
            new RolePermissionBundleCatalog.BundleDocument(1, List.of(
                new RolePermissionBundleCatalog.BundleDefinition(
                    "USER_READ_ONLY", "用户管理只读", 10, List.of("USER_READ", "MENU_VIEW_USER_MANAGEMENT")
                )
            )),
            List.of(
                permission(11L, "USER_READ"),
                permission(12L, "MENU_VIEW_USER_MANAGEMENT")
            )
        );

        assertThat(catalog.version()).isEqualTo(1);
        assertThat(catalog.bundles()).singleElement().satisfies(bundle -> {
            assertThat(bundle.id()).isEqualTo("USER_READ_ONLY");
            assertThat(bundle.permissionCodes()).containsExactly("USER_READ", "MENU_VIEW_USER_MANAGEMENT");
            assertThat(bundle.permissionIds()).containsExactly(11L, 12L);
        });
    }

    @Test
    void rejectsUnknownPermissionCodes() {
        RolePermissionBundleCatalog.BundleDocument document = new RolePermissionBundleCatalog.BundleDocument(
            1,
            List.of(new RolePermissionBundleCatalog.BundleDefinition(
                "BROKEN", "错误套件", 10, List.of("NOT_FOUND")
            ))
        );

        assertThatThrownBy(() -> new RolePermissionBundleCatalog(document, List.of(permission(11L, "USER_READ"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("NOT_FOUND");
    }

    @Test
    void rejectsDuplicateBundleIdsAndDuplicateCodesInsideABundle() {
        RolePermissionBundleCatalog.BundleDocument duplicateIds = new RolePermissionBundleCatalog.BundleDocument(
            1,
            List.of(
                new RolePermissionBundleCatalog.BundleDefinition("DUPLICATE", "一", 10, List.of("USER_READ")),
                new RolePermissionBundleCatalog.BundleDefinition("DUPLICATE", "二", 20, List.of("ROLE_READ"))
            )
        );
        assertThatThrownBy(() -> new RolePermissionBundleCatalog(
            duplicateIds,
            List.of(permission(11L, "USER_READ"), permission(12L, "ROLE_READ"))
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("DUPLICATE");

        RolePermissionBundleCatalog.BundleDocument duplicateCodes = new RolePermissionBundleCatalog.BundleDocument(
            1,
            List.of(new RolePermissionBundleCatalog.BundleDefinition(
                "DUPLICATE_CODE", "重复编码", 10, List.of("USER_READ", "USER_READ")
            ))
        );
        assertThatThrownBy(() -> new RolePermissionBundleCatalog(
            duplicateCodes,
            List.of(permission(11L, "USER_READ"))
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("USER_READ");
    }

    private Permission permission(Long id, String code) {
        return Permission.builder()
            .id(id)
            .permissionCode(code)
            .permissionName(code)
            .permissionType(PermissionType.API)
            .status(1)
            .build();
    }
}
