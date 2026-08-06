package com.company.idm.infrastructure.casbin;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.casbin.jcasbin.main.Enforcer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 负责将用户角色和角色权限刷新到 Casbin 中。
 */
@Service
@RequiredArgsConstructor
public class CasbinPolicyService implements PolicyRefreshService {

    private final Enforcer enforcer;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    @Override
    public void refresh() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refreshNow();
                }
            });
            return;
        }
        refreshNow();
    }

    public synchronized boolean enforce(String userId, String permissionCode, String action) {
        return enforcer.enforce(userId, permissionCode, action);
    }

    private synchronized void refreshNow() {
        enforcer.clearPolicy();
        permissionRepository.listRolePolicies()
            .forEach(policy -> enforcer.addPolicy(policy.roleCode(), policy.permissionCode(), "GRANT"));
        userRepository.listUserRoleBindings()
            .forEach(binding -> enforcer.addGroupingPolicy(binding.userId(), binding.roleCode()));
        enforcer.buildRoleLinks();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        refreshNow();
    }
}

