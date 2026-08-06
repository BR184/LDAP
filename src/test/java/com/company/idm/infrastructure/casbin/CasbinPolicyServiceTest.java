package com.company.idm.infrastructure.casbin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.user.UserRepository;
import java.util.List;
import org.casbin.jcasbin.main.Enforcer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CasbinPolicyServiceTest {

    @Test
    void refreshesOnlyAfterTheSurroundingTransactionCommits() {
        Enforcer enforcer = mock(Enforcer.class);
        PermissionRepository permissionRepository = mock(PermissionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        CasbinPolicyService service = new CasbinPolicyService(enforcer, permissionRepository, userRepository);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.refresh();

            verify(enforcer, never()).clearPolicy();
            List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(TransactionSynchronization::afterCommit);

            verify(enforcer).clearPolicy();
            verify(enforcer).buildRoleLinks();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
}
