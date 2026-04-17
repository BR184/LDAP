package com.company.idm.test;

import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.rbac.RolePolicy;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.domain.user.UserRoleBinding;
import com.company.idm.infrastructure.casbin.CasbinPolicyService;
import java.util.List;
import org.casbin.jcasbin.main.Enforcer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Casbin 策略刷新服务行为的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class CasbinPolicyServiceTest {

    @Mock
    private Enforcer enforcer;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CasbinPolicyService casbinPolicyService;

    @Test
    void shouldRefreshCasbinPoliciesAndBindings() {
        when(permissionRepository.listRolePolicies()).thenReturn(List.of(
            new RolePolicy("ADMIN", "/api/v1/users", "GET"),
            new RolePolicy("ADMIN", "/api/v1/departments", "POST")
        ));
        when(userRepository.listUserRoleBindings()).thenReturn(List.of(
            new UserRoleBinding("admin", "ADMIN"),
            new UserRoleBinding("zhangsan", "NORMAL_USER")
        ));

        casbinPolicyService.refresh();

        verify(enforcer).clearPolicy();
        verify(enforcer).addPolicy("ADMIN", "/api/v1/users", "GET");
        verify(enforcer).addPolicy("ADMIN", "/api/v1/departments", "POST");
        verify(enforcer).addGroupingPolicy("admin", "ADMIN");
        verify(enforcer).addGroupingPolicy("zhangsan", "NORMAL_USER");
        verify(enforcer).buildRoleLinks();
    }

    @Test
    void shouldRefreshPoliciesOnInit() {
        when(permissionRepository.listRolePolicies()).thenReturn(List.of(
            new RolePolicy("ADMIN", "/api/v1/auth/me", "GET")
        ));
        when(userRepository.listUserRoleBindings()).thenReturn(List.of(
            new UserRoleBinding("admin", "ADMIN")
        ));

        casbinPolicyService.init();

        verify(enforcer).clearPolicy();
        verify(enforcer).addPolicy("ADMIN", "/api/v1/auth/me", "GET");
        verify(enforcer).addGroupingPolicy("admin", "ADMIN");
        verify(enforcer).buildRoleLinks();
    }
}
