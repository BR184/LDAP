package com.company.idm.application.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.idm.common.enums.PermissionType;
import com.company.idm.domain.rbac.Permission;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RolePermissionBundleCatalogTest {

    private static final String RESOURCE_PATH = "/security/role-permission-bundles.json";

    @Test
    void productionCatalogDefinesTenModulesWithStandardAndAdminTiers() throws Exception {
        RolePermissionBundleCatalog.BundleDocument document = loadProductionDocument();
        Set<String> codes = new LinkedHashSet<>();
        document.categories().stream()
            .flatMap(category -> category.bundles().stream())
            .flatMap(bundle -> bundle.permissionCodes().stream())
            .forEach(codes::add);
        AtomicLong sequence = new AtomicLong(1L);
        RolePermissionBundleCatalog catalog = new RolePermissionBundleCatalog(
            document,
            codes.stream().map(code -> permission(sequence.getAndIncrement(), code)).toList()
        );

        assertThat(catalog.version()).isEqualTo(2);
        assertThat(catalog.bundles()).hasSize(20);
        assertThat(catalog.bundles()).extracting(RolePermissionBundle::categoryId).containsOnly(
            "USER_MANAGEMENT",
            "DEPARTMENT_MANAGEMENT",
            "ROLE_MANAGEMENT",
            "PERMISSION_ASSIGNMENT",
            "ROLE_GROUP_MANAGEMENT",
            "MENU_MANAGEMENT",
            "FILE_IMPORT",
            "SYNC_OPERATIONS",
            "LDAP_CONTROL",
            "MAIL_CONFIGURATION"
        );
        assertThat(catalog.bundles().stream().filter(bundle -> bundle.tier() == RolePermissionBundleTier.STANDARD))
            .hasSize(10);
        assertThat(catalog.bundles().stream().filter(bundle -> bundle.tier() == RolePermissionBundleTier.ADMIN))
            .hasSize(10);

        catalog.bundles().stream()
            .collect(java.util.stream.Collectors.groupingBy(RolePermissionBundle::categoryId))
            .values()
            .forEach(moduleBundles -> {
                RolePermissionBundle standard = bundle(moduleBundles, RolePermissionBundleTier.STANDARD);
                RolePermissionBundle admin = bundle(moduleBundles, RolePermissionBundleTier.ADMIN);
                assertThat(admin.permissionCodes()).containsAll(standard.permissionCodes());
                assertThat(admin.permissionCodes()).hasSizeGreaterThan(standard.permissionCodes().size());
            });
    }

    @Test
    void resolvesExplicitPermissionOrderAndPublishesModuleMetadata() {
        RolePermissionBundleCatalog catalog = new RolePermissionBundleCatalog(
            document(category(
                "USER_MANAGEMENT",
                bundleDefinition(
                    "USER_STANDARD", RolePermissionBundleTier.STANDARD,
                    List.of("USER_READ", "MENU_VIEW_USER_MANAGEMENT")
                ),
                bundleDefinition(
                    "USER_ADMIN", RolePermissionBundleTier.ADMIN,
                    List.of("USER_READ", "MENU_VIEW_USER_MANAGEMENT", "USER_CREATE")
                )
            )),
            List.of(
                permission(11L, "USER_READ"),
                permission(12L, "MENU_VIEW_USER_MANAGEMENT"),
                permission(13L, "USER_CREATE")
            )
        );

        assertThat(catalog.bundles()).first().satisfies(bundle -> {
            assertThat(bundle.categoryId()).isEqualTo("USER_MANAGEMENT");
            assertThat(bundle.categoryName()).isEqualTo("用户管理");
            assertThat(bundle.categoryDescription()).isEqualTo("用户查询与账号生命周期管理");
            assertThat(bundle.categorySort()).isEqualTo(10);
            assertThat(bundle.tier()).isEqualTo(RolePermissionBundleTier.STANDARD);
            assertThat(bundle.permissionCodes()).containsExactly("USER_READ", "MENU_VIEW_USER_MANAGEMENT");
            assertThat(bundle.permissionIds()).containsExactly(11L, 12L);
        });
    }

    @Test
    void rejectsUnknownPermissionsDuplicateIdsAndInvalidTierSets() {
        RolePermissionBundleCatalog.BundleDocument unknownPermission = document(category(
            "USER_MANAGEMENT",
            bundleDefinition("USER_STANDARD", RolePermissionBundleTier.STANDARD, List.of("NOT_FOUND")),
            bundleDefinition("USER_ADMIN", RolePermissionBundleTier.ADMIN, List.of("NOT_FOUND", "USER_CREATE"))
        ));
        assertThatThrownBy(() -> new RolePermissionBundleCatalog(
            unknownPermission,
            List.of(permission(1L, "USER_CREATE"))
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("NOT_FOUND");

        RolePermissionBundleCatalog.BundleDocument duplicateCategory = new RolePermissionBundleCatalog.BundleDocument(
            2,
            List.of(
                category(
                    "DUPLICATE",
                    bundleDefinition("ONE_STANDARD", RolePermissionBundleTier.STANDARD, List.of("USER_READ")),
                    bundleDefinition("ONE_ADMIN", RolePermissionBundleTier.ADMIN, List.of("USER_READ", "USER_CREATE"))
                ),
                category(
                    "DUPLICATE",
                    bundleDefinition("TWO_STANDARD", RolePermissionBundleTier.STANDARD, List.of("ROLE_READ")),
                    bundleDefinition("TWO_ADMIN", RolePermissionBundleTier.ADMIN, List.of("ROLE_READ", "ROLE_CREATE"))
                )
            )
        );
        assertThatThrownBy(() -> new RolePermissionBundleCatalog(
            duplicateCategory,
            List.of(
                permission(1L, "USER_READ"), permission(2L, "USER_CREATE"),
                permission(3L, "ROLE_READ"), permission(4L, "ROLE_CREATE")
            )
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("DUPLICATE");

        RolePermissionBundleCatalog.BundleDocument invalidSuperset = document(category(
            "USER_MANAGEMENT",
            bundleDefinition(
                "USER_STANDARD", RolePermissionBundleTier.STANDARD,
                List.of("USER_READ", "USER_DETAIL")
            ),
            bundleDefinition(
                "USER_ADMIN", RolePermissionBundleTier.ADMIN,
                List.of("USER_READ", "USER_CREATE")
            )
        ));
        assertThatThrownBy(() -> new RolePermissionBundleCatalog(
            invalidSuperset,
            List.of(
                permission(1L, "USER_READ"), permission(2L, "USER_DETAIL"), permission(3L, "USER_CREATE")
            )
        )).isInstanceOf(IllegalStateException.class).hasMessageContaining("strict superset");
    }

    private RolePermissionBundleCatalog.BundleDocument loadProductionDocument() throws Exception {
        try (InputStream input = getClass().getResourceAsStream(RESOURCE_PATH)) {
            return new ObjectMapper().readValue(input, RolePermissionBundleCatalog.BundleDocument.class);
        }
    }

    private RolePermissionBundle bundle(List<RolePermissionBundle> bundles, RolePermissionBundleTier tier) {
        return bundles.stream().filter(bundle -> bundle.tier() == tier).findFirst().orElseThrow();
    }

    private RolePermissionBundleCatalog.BundleDocument document(
        RolePermissionBundleCatalog.CategoryDefinition... categories
    ) {
        return new RolePermissionBundleCatalog.BundleDocument(2, List.of(categories));
    }

    private RolePermissionBundleCatalog.CategoryDefinition category(
        String id,
        RolePermissionBundleCatalog.BundleDefinition... bundles
    ) {
        return new RolePermissionBundleCatalog.CategoryDefinition(
            id,
            "用户管理",
            "用户查询与账号生命周期管理",
            10,
            List.of(bundles)
        );
    }

    private RolePermissionBundleCatalog.BundleDefinition bundleDefinition(
        String id,
        RolePermissionBundleTier tier,
        List<String> permissionCodes
    ) {
        return new RolePermissionBundleCatalog.BundleDefinition(
            id,
            tier == RolePermissionBundleTier.STANDARD ? "常规操作" : "完整管理",
            tier,
            tier == RolePermissionBundleTier.STANDARD ? 10 : 20,
            permissionCodes
        );
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
