package com.company.idm.application.rbac;

import com.company.idm.domain.rbac.Permission;
import com.company.idm.domain.rbac.PermissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
        if (document == null || document.version() <= 0 || document.bundles() == null) {
            throw new IllegalStateException("Role permission bundle resource is invalid");
        }
        if (permissions == null) {
            throw new IllegalStateException("Permission list must not be null");
        }
        Map<String, Permission> permissionsByCode = permissions.stream()
            .filter(permission -> permission.getPermissionCode() != null && !permission.getPermissionCode().isBlank())
            .collect(Collectors.toMap(Permission::getPermissionCode, Function.identity(), (first, ignored) -> first));
        validateDefinitions(document.bundles(), permissionsByCode.keySet());
        this.version = document.version();
        this.bundles = document.bundles().stream()
            .map(definition -> toBundle(definition, permissionsByCode))
            .sorted(Comparator.comparingInt(RolePermissionBundle::sort))
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

    private static void validateDefinitions(List<BundleDefinition> definitions, Set<String> knownPermissionCodes) {
        Set<String> bundleIds = new HashSet<>();
        for (BundleDefinition definition : definitions) {
            if (definition == null || definition.id() == null || definition.id().isBlank()) {
                throw new IllegalStateException("Role permission bundle id must not be blank");
            }
            if (!bundleIds.add(definition.id())) {
                throw new IllegalStateException("Duplicate role permission bundle: " + definition.id());
            }
            if (definition.name() == null || definition.name().isBlank() || definition.sort() < 0
                || definition.permissionCodes() == null || definition.permissionCodes().isEmpty()) {
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
    }

    private static RolePermissionBundle toBundle(
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
            definition.permissionCodes()
        );
    }

    public record BundleDocument(int version, List<BundleDefinition> bundles) {
    }

    public record BundleDefinition(String id, String name, int sort, List<String> permissionCodes) {
    }
}
