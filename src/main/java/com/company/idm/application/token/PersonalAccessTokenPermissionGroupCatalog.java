package com.company.idm.application.token;

import com.company.idm.common.enums.PermissionType;
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
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Loads the versioned permission grouping resource and enforces complete API coverage at startup.
 */
@Component
public class PersonalAccessTokenPermissionGroupCatalog {

    private static final String RESOURCE_PATH = "security/personal-access-token-permission-groups.json";

    private final int version;
    private final List<PersonalAccessTokenPermissionGroup> groups;

    @Autowired
    public PersonalAccessTokenPermissionGroupCatalog(
        ObjectMapper objectMapper,
        PermissionRepository permissionRepository
    ) {
        this(loadDocument(objectMapper, new ClassPathResource(RESOURCE_PATH)), permissionRepository.findAll());
    }

    PersonalAccessTokenPermissionGroupCatalog(
        PermissionGroupDocument document,
        Collection<Permission> enabledPermissions
    ) {
        if (document == null || document.version() <= 0 || document.groups() == null) {
            throw new IllegalStateException("Personal access token permission group resource is invalid");
        }
        this.version = document.version();
        this.groups = document.groups().stream()
            .map(PersonalAccessTokenPermissionGroupCatalog::toGroup)
            .sorted(Comparator.comparingInt(PersonalAccessTokenPermissionGroup::sort))
            .toList();
        validateGroupDefinitions(this.groups);
        validateApiCoverage(this.groups, enabledPermissions);
    }

    public int version() {
        return version;
    }

    public List<PersonalAccessTokenPermissionGroup> groups() {
        return groups;
    }

    public Set<String> apiPermissionCodes() {
        return groups.stream()
            .flatMap(group -> group.permissionCodes().stream())
            .collect(Collectors.toUnmodifiableSet());
    }

    public List<PersonalAccessTokenPermissionGroup> intersect(Collection<String> ownedPermissionCodes) {
        Set<String> owned = ownedPermissionCodes == null ? Set.of() : Set.copyOf(ownedPermissionCodes);
        return groups.stream()
            .map(group -> Map.entry(group, group.permissionCodes().stream().filter(owned::contains).toList()))
            .filter(entry -> !entry.getValue().isEmpty())
            .map(entry -> new PersonalAccessTokenPermissionGroup(
                entry.getKey().id(),
                entry.getKey().name(),
                entry.getKey().risk(),
                entry.getKey().sort(),
                entry.getValue()
            ))
            .toList();
    }

    private static PermissionGroupDocument loadDocument(ObjectMapper objectMapper, Resource resource) {
        try (var inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, PermissionGroupDocument.class);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load personal access token permission groups", exception);
        }
    }

    private static PersonalAccessTokenPermissionGroup toGroup(PermissionGroupDefinition definition) {
        try {
            return new PersonalAccessTokenPermissionGroup(
                definition.id(),
                definition.name(),
                PersonalAccessTokenPermissionRisk.valueOf(definition.risk()),
                definition.sort(),
                definition.permissionCodes()
            );
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Personal access token permission group definition is invalid", exception);
        }
    }

    private static void validateGroupDefinitions(List<PersonalAccessTokenPermissionGroup> groups) {
        Set<String> groupIds = new HashSet<>();
        Map<String, String> ownersByCode = new HashMap<>();
        for (PersonalAccessTokenPermissionGroup group : groups) {
            if (!groupIds.add(group.id())) {
                throw new IllegalStateException("Duplicate personal access token permission group: " + group.id());
            }
            for (String code : group.permissionCodes()) {
                String previousOwner = ownersByCode.putIfAbsent(code, group.id());
                if (previousOwner != null) {
                    throw new IllegalStateException(
                        "Permission belongs to multiple personal access token groups: " + code
                    );
                }
            }
        }
    }

    private static void validateApiCoverage(
        List<PersonalAccessTokenPermissionGroup> groups,
        Collection<Permission> enabledPermissions
    ) {
        if (enabledPermissions == null) {
            throw new IllegalStateException("Enabled permission list must not be null");
        }
        Set<String> apiCodes = enabledPermissions.stream()
            .filter(permission -> permission.getPermissionType() == PermissionType.API)
            .map(Permission::getPermissionCode)
            .filter(code -> code != null && !code.isBlank())
            .collect(Collectors.toSet());
        Set<String> groupedCodes = groups.stream()
            .flatMap(group -> group.permissionCodes().stream())
            .collect(Collectors.toSet());
        Set<String> missing = new HashSet<>(apiCodes);
        missing.removeAll(groupedCodes);
        Set<String> unknown = new HashSet<>(groupedCodes);
        unknown.removeAll(apiCodes);
        if (!missing.isEmpty() || !unknown.isEmpty()) {
            throw new IllegalStateException(
                "Personal access token permission groups do not match enabled API permissions: missing="
                    + missing + ", unknown=" + unknown
            );
        }
    }

    public record PermissionGroupDocument(int version, List<PermissionGroupDefinition> groups) {
    }

    public record PermissionGroupDefinition(
        String id,
        String name,
        String risk,
        int sort,
        List<String> permissionCodes
    ) {
    }
}
