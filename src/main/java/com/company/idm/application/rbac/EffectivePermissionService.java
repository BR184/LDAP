package com.company.idm.application.rbac;

import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Resolves request permissions from the current account and credential scope.
 */
@Service
@RequiredArgsConstructor
public class EffectivePermissionService {

    private final PermissionRepository permissionRepository;

    public Set<String> resolve(AuthenticatedUser principal) {
        if (principal == null || principal.userId() == null || principal.userId().isBlank()) {
            return Set.of();
        }
        Set<String> accountPermissions = permissionRepository.findPermissionCodesByUserId(principal.userId());
        if (principal.credentialType() != CredentialType.PERSONAL_ACCESS_TOKEN) {
            return accountPermissions;
        }
        Set<String> effectivePermissions = new HashSet<>(accountPermissions);
        effectivePermissions.retainAll(principal.selectedPermissionCodes() == null
            ? Set.of()
            : principal.selectedPermissionCodes());
        return Set.copyOf(effectivePermissions);
    }
}
