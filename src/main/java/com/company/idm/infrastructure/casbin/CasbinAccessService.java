package com.company.idm.infrastructure.casbin;

import com.company.idm.application.rbac.EffectivePermissionService;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import java.util.Arrays;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.casbin.jcasbin.main.Enforcer;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * 基于 Casbin 执行接口访问授权校验。
 */
@Service("casbinAccessService")
@RequiredArgsConstructor
public class CasbinAccessService {

    private final Enforcer enforcer;
    private final EffectivePermissionService effectivePermissionService;

    public boolean hasAny(Authentication authentication, String... permissionCodes) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return false;
        }
        if (permissionCodes == null || permissionCodes.length == 0) {
            return false;
        }
        Set<String> effectivePermissions = effectivePermissionService.resolve(principal);
        return Arrays.stream(permissionCodes)
            .filter(effectivePermissions::contains)
            .anyMatch(permissionCode -> enforcer.enforce(principal.userId(), permissionCode, "GRANT"));
    }

}

