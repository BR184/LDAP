package com.company.idm.application.rbac;

import com.company.idm.domain.rbac.Permission;
import com.company.idm.domain.rbac.PermissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class RolePermissionBundleCatalog {

    private static final String RESOURCE_PATH = "security/role-permission-bundles.json";

    private final int version;
    private final List<RolePermissionBundle> bundles;

    @Autowired
    public RolePermissionBundleCatalog(ObjectMapper objectMapper, PermissionRepository permissionRepository) {
        this(loadDocument(objectMapper, new ClassPathResource(RESOURCE_PATH)), permissionRepository.findAll());
    }

    RolePermissionBundleCatalog(BundleDocument document, Collection<Permission> permissions) {
        if (document == null || document.version() <= 0 || document.categories() == null
            || document.categories().isEmpty()) {
            throw new IllegalStateException("Role permission bundle resource is invalid");
        }
        if (permissions == null) {
            throw new IllegalStateException("Permission list must not be null");
        }
        Map<String, Permission> permissionsByCode = permissions.stream()
            .filter(permission -> permission.getPermissionCode() != null && !permission.getPermissionCode().isBlank())
            .collect(Collectors.toMap(Permission::getPermissionCode, Function.identity(), (first, ignored) -> first));
        validateDefinitions(document.categories(), permissionsByCode.keySet());
        this.version = document.version();
        this.bundles = document.categories().stream()
            .sorted(Comparator.comparingInt(CategoryDefinition::sort))
            .flatMap(category -> category.bundles().stream()
                .sorted(Comparator.comparingInt(BundleDefinition::sort))
                .map(definition -> toBundle(category, definition, permissionsByCode)))
            .toList();
    }

    public int version() {
        return version;
    }

    public List<RolePermissionBundle> bundles() {
        return bundles;
    }

    private static BundleDocument loadDocument(ObjectMapper objectMapper, Resource resource) {
        try (var inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, BundleDocument.class);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load role permission bundles", exception);
        }
    }

    private static void validateDefinitions(
        List<CategoryDefinition> categories,
        Set<String> knownPermissionCodes
    ) {
        Set<String> categoryIds = new HashSet<>();
        Set<String> bundleIds = new HashSet<>();
        for (CategoryDefinition category : categories) {
            validateCategoryMetadata(category, categoryIds);
            Map<RolePermissionBundleTier, BundleDefinition> definitionsByTier = new EnumMap<>(
                RolePermissionBundleTier.class
            );
            for (BundleDefinition definition : category.bundles()) {
                validateBundleDefinition(category.id(), definition, bundleIds, knownPermissionCodes);
                if (definitionsByTier.put(definition.tier(), definition) != null) {
                    throw new IllegalStateException(
                        "Duplicate role permission bundle tier in category " + category.id() + ": " + definition.tier()
                    );
                }
            }
            if (definitionsByTier.size() != RolePermissionBundleTier.values().length) {
                throw new IllegalStateException(
                    "Role permission bundle category must define STANDARD and ADMIN: " + category.id()
                );
            }
            validateStrictSuperset(
                category.id(),
                definitionsByTier.get(RolePermissionBundleTier.STANDARD),
                definitionsByTier.get(RolePermissionBundleTier.ADMIN)
            );
        }
    }

    private static void validateCategoryMetadata(CategoryDefinition category, Set<String> categoryIds) {
        if (category == null || category.id() == null || category.id().isBlank()) {
            throw new IllegalStateException("Role permission bundle category id must not be blank");
        }
        if (!categoryIds.add(category.id())) {
            throw new IllegalStateException("Duplicate role permission bundle category: " + category.id());
        }
        if (category.name() == null || category.name().isBlank()
            || category.description() == null || category.description().isBlank()
            || category.sort() < 0 || category.bundles() == null || category.bundles().isEmpty()) {
            throw new IllegalStateException("Role permission bundle category is invalid: " + category.id());
        }
    }

    private static void validateBundleDefinition(
        String categoryId,
        BundleDefinition definition,
        Set<String> bundleIds,
        Set<String> knownPermissionCodes
    ) {
        if (definition == null || definition.id() == null || definition.id().isBlank()) {
            throw new IllegalStateException("Role permission bundle id must not be blank in category " + categoryId);
        }
        if (!bundleIds.add(definition.id())) {
            throw new IllegalStateException("Duplicate role permission bundle: " + definition.id());
        }
        if (definition.name() == null || definition.name().isBlank() || definition.tier() == null
            || definition.sort() < 0 || definition.permissionCodes() == null || definition.permissionCodes().isEmpty()) {
            throw new IllegalStateException("Role permission bundle definition is invalid: " + definition.id());
        }
        Set<String> codes = new HashSet<>();
        for (String permissionCode : definition.permissionCodes()) {
            if (permissionCode == null || permissionCode.isBlank() || !codes.add(permissionCode)) {
                throw new IllegalStateException(
                    "Duplicate or blank permission code in role permission bundle "
                        + definition.id() + ": " + permissionCode
                );
            }
            if (!knownPermissionCodes.contains(permissionCode)) {
                throw new IllegalStateException(
                    "Unknown permission code in role permission bundle "
                        + definition.id() + ": " + permissionCode
                );
            }
        }
    }

    private static void validateStrictSuperset(
        String categoryId,
        BundleDefinition standard,
        BundleDefinition admin
    ) {
        Set<String> standardCodes = Set.copyOf(standard.permissionCodes());
        Set<String> adminCodes = Set.copyOf(admin.permissionCodes());
        if (!adminCodes.containsAll(standardCodes) || adminCodes.size() <= standardCodes.size()) {
            throw new IllegalStateException(
                "ADMIN permission bundle must be a strict superset of STANDARD in category " + categoryId
            );
        }
    }

    private static RolePermissionBundle toBundle(
        CategoryDefinition category,
        BundleDefinition definition,
        Map<String, Permission> permissionsByCode
    ) {
        List<Long> permissionIds = definition.permissionCodes().stream()
            .map(permissionsByCode::get)
            .map(Permission::getId)
            .toList();
        return new RolePermissionBundle(
            definition.id(),
            definition.name(),
            definition.sort(),
            permissionIds,
            definition.permissionCodes(),
            category.id(),
            category.name(),
            category.description(),
            category.sort(),
            definition.tier()
        );
    }

    public record BundleDocument(int version, List<CategoryDefinition> categories) {
    }

    public record CategoryDefinition(
        String id,
        String name,
        String description,
        int sort,
        List<BundleDefinition> bundles
    ) {
    }

    public record BundleDefinition(
        String id,
        String name,
        RolePermissionBundleTier tier,
        int sort,
        List<String> permissionCodes
    ) {
    }
}
