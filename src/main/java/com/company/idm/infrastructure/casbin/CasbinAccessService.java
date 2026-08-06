package com.company.idm.infrastructure.casbin;

import com.company.idm.application.rbac.EffectivePermissionService;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * 基于 Casbin 执行接口访问授权校验。
 */
@Service("casbinAccessService")
@RequiredArgsConstructor
public class CasbinAccessService {

    private final CasbinPolicyService policyService;
    private final EffectivePermissionService effectivePermissionService;

    public boolean hasAny(Authentication authentication, String... permissionCodes) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return false;
        }
        if (permissionCodes == null || permissionCodes.length == 0) {
            return false;
        }
        Set<String> effectivePermissions = effectivePermissionService.resolve(principal);
        List<String> candidates = Arrays.stream(permissionCodes)
            .filter(effectivePermissions::contains)
            .toList();
        if (candidates.isEmpty()) {
            return false;
        }
        boolean granted = candidates.stream()
            .anyMatch(permissionCode -> policyService.enforce(principal.userId(), permissionCode, "GRANT"));
        if (granted) {
            return true;
        }

        // The database is authoritative. A false negative can occur when another app instance changed RBAC state.
        policyService.refresh();
        return candidates.stream()
            .anyMatch(permissionCode -> policyService.enforce(principal.userId(), permissionCode, "GRANT"));
    }

}

