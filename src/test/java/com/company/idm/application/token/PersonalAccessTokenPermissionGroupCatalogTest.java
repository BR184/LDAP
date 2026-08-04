package com.company.idm.application.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.idm.common.enums.PermissionType;
import com.company.idm.domain.rbac.Permission;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersonalAccessTokenPermissionGroupCatalogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void groupsEveryEnabledApiPermissionExactlyOnceAndExcludesMenus() throws Exception {
        PersonalAccessTokenPermissionGroupCatalog.PermissionGroupDocument document = document();
        List<Permission> permissions = allApiPermissions(document);
        permissions.add(Permission.builder()
            .permissionCode("MENU_VIEW_USER_MANAGEMENT")
            .permissionType(PermissionType.MENU)
            .build());
        PersonalAccessTokenPermissionGroupCatalog catalog =
            new PersonalAccessTokenPermissionGroupCatalog(document, permissions);

        assertThat(catalog.intersect(List.of("AUTH_ME", "USER_READ", "MENU_VIEW_USER_MANAGEMENT")))
            .extracting(PersonalAccessTokenPermissionGroup::id)
            .containsExactly("ACCOUNT_READ");
        assertThat(catalog.groups().stream().flatMap(group -> group.permissionCodes().stream()))
            .doesNotContain("MENU_VIEW_USER_MANAGEMENT");
    }

    @Test
    void exposesDelegatedRoleGroupReadAsALowRiskPermission() throws Exception {
        PersonalAccessTokenPermissionGroupCatalog.PermissionGroupDocument document = document();

        PersonalAccessTokenPermissionGroupCatalog.PermissionGroupDefinition group = document.groups().stream()
            .filter(candidate -> candidate.permissionCodes().contains("ROLE_GROUP_READ"))
            .findFirst()
            .orElseThrow();

        assertThat(group.id()).isEqualTo("DELEGATED_ROLE_READ");
        assertThat(group.risk()).isEqualTo("LOW");
    }

    @Test
    void rejectsAnUngroupedEnabledApiPermission() throws Exception {
        PersonalAccessTokenPermissionGroupCatalog.PermissionGroupDocument document = document();
        List<Permission> permissions = allApiPermissions(document);
        permissions.add(Permission.builder().permissionCode("NOT_GROUPED").permissionType(PermissionType.API).build());

        assertThatThrownBy(() -> new PersonalAccessTokenPermissionGroupCatalog(document, permissions))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("NOT_GROUPED");
    }

    @Test
    void rejectsDuplicateGroupMembership() throws Exception {
        PersonalAccessTokenPermissionGroupCatalog.PermissionGroupDocument document =
            new PersonalAccessTokenPermissionGroupCatalog.PermissionGroupDocument(
                1,
                List.of(
                    new PersonalAccessTokenPermissionGroupCatalog.PermissionGroupDefinition(
                        "A", "A", "LOW", 1, List.of("AUTH_ME")
                    ),
                    new PersonalAccessTokenPermissionGroupCatalog.PermissionGroupDefinition(
                        "B", "B", "HIGH", 2, List.of("AUTH_ME")
                    )
                )
            );

        assertThatThrownBy(() -> new PersonalAccessTokenPermissionGroupCatalog(
            document,
            List.of(Permission.builder().permissionCode("AUTH_ME").permissionType(PermissionType.API).build())
        )).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("multiple");
    }

    private PersonalAccessTokenPermissionGroupCatalog.PermissionGroupDocument document() throws Exception {
        return MAPPER.readValue(
            getClass().getResourceAsStream("/security/personal-access-token-permission-groups.json"),
            PersonalAccessTokenPermissionGroupCatalog.PermissionGroupDocument.class
        );
    }

    private List<Permission> allApiPermissions(
        PersonalAccessTokenPermissionGroupCatalog.PermissionGroupDocument document
    ) {
        return document.groups().stream()
            .flatMap(group -> group.permissionCodes().stream())
            .map(code -> Permission.builder().permissionCode(code).permissionType(PermissionType.API).build())
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }
}
