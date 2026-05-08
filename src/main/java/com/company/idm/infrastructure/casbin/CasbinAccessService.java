package com.company.idm.infrastructure.casbin;

import com.company.idm.infrastructure.security.AuthenticatedUser;
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

    public boolean check(Authentication authentication, String obj, String act) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return false;
        }
        return enforcer.enforce(principal.userId(), obj, act);
    }
}

