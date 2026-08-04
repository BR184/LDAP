package com.company.idm.application.sync.importplan;

import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.application.sync.LeaderRoleDerivationService;
import com.company.idm.application.user.InitialPasswordPolicy;
import com.company.idm.application.user.IntranetEmailGenerationService;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ChangeType;
import com.company.idm.domain.sync.ImportBatch;
import com.company.idm.domain.sync.ImportBatchRepository;
import com.company.idm.domain.sync.TargetType;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserAccessPolicy;
import com.company.idm.domain.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImportExecutionApplicationService {

    private static final String NORMAL_USER_ROLE_CODE = "NORMAL_USER";

    private final ImportBatchRepository importBatchRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LdapDirectoryService ldapDirectoryService;
    private final LdapGroupService ldapGroupService;
    private final PolicyRefreshService policyRefreshService;
    private final LeaderRoleDerivationService leaderRoleDerivationService;
    private final ImportJsonService jsonService;
    private final InitialPasswordPolicy initialPasswordPolicy;
    private final UserAccessPolicy userAccessPolicy;
    private final IntranetEmailGenerationService intranetEmailGenerationService;

    @Transactional
    public ImportBatch executePlan(Long batchId, String executedBy) {
        ImportBatch batch = importBatchRepository.findById(batchId)
            .orElseThrow(() -> new BizException("IMPORT_BATCH_NOT_FOUND", "导入批次不存在"));
        batch.execute(executedBy);
        batch = importBatchRepository.save(batch);

        for (ChangeItem item : batch.getEnabledChangeItems()) {
            try {
                executeChangeItem(item);
                item.markExecuted();
            } catch (LdapImportException exception) {
                item.markLdapFailed(exception.getMessage());
            } catch (RuntimeException exception) {
                item.markFailed(resolveMessage(exception));
            }
        }
        batch.complete();
        ImportBatch saved = importBatchRepository.save(batch);
        leaderRoleDerivationService.syncDerivedRoles();
        syncBaselineNormalUserRole();
        policyRefreshService.refresh();
        return saved;
    }

    private void executeChangeItem(ChangeItem item) {
        if (item.getChangeType() == ChangeType.CREATE || item.getChangeType() == ChangeType.UPDATE) {
            if (item.getTargetType() == TargetType.DEPARTMENT) {
                executeDepartment(item);
                return;
            }
            if (item.getTargetType() == TargetType.USER) {
                executeUser(item);
                return;
            }
        }
        if (item.getChangeType() == ChangeType.RESIGN) {
            executeResign(item);
            return;
        }
    }

    private void executeDepartment(ChangeItem item) {
        Department target = jsonService.readDepartmentSnapshot(item.getAfterJson()).toDepartment();
        Department saved = departmentRepository.save(target);
        try {
            String ldapDn = ldapGroupService.createGroup(saved.getDeptCode(), saved.getDeptName());
            if (ldapDn != null && !ldapDn.equals(saved.getLdapDn())) {
                departmentRepository.save(saved.toBuilder().ldapDn(ldapDn).build());
            }
        } catch (RuntimeException exception) {
            throw new LdapImportException(resolveMessage(exception));
        }
    }

    private void executeUser(ChangeItem item) {
        UserImportSnapshot snapshot = jsonService.readUserSnapshot(item.getAfterJson());
        if (snapshot == null) {
            throw new BizException("IMPORT_USER_SNAPSHOT_MISSING", "用户导入快照不存在");
        }
        User existing = userRepository.findByUserId(snapshot.userId()).orElse(null);
        String intranetEmail = existing == null
            ? intranetEmailGenerationService.generate(snapshot.userId(), null)
            : existing.getIntranetEmail();
        if (intranetEmail == null || intranetEmail.isBlank()) {
            intranetEmail = intranetEmailGenerationService.generate(
                existing == null ? snapshot.userId() : existing.getUserId(),
                existing == null ? null : existing.getId()
            );
        }
        User target = snapshot.applyTo(existing, intranetEmail);
        User saved = userRepository.save(target);
        try {
            String ldapDn = ldapDirectoryService.createOrUpdateUser(saved, initialPasswordPolicy.resolve(saved.getMobile()));
            if (ldapDn != null && !ldapDn.equals(saved.getLdapDn())) {
                saved = userRepository.save(saved.toBuilder().ldapDn(ldapDn).build());
            }
            syncUserLdapState(saved);
        } catch (RuntimeException exception) {
            throw new LdapImportException(resolveMessage(exception));
        }
    }

    private void syncBaselineNormalUserRole() {
        Role role = roleRepository.findByCode(NORMAL_USER_ROLE_CODE)
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "普通用户角色不存在"));
        java.util.Set<Long> activeUserIds = userRepository.findActiveEmployees().stream()
            .map(User::getId)
            .filter(java.util.Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        userRepository.syncRoleBindings(role.getId(), activeUserIds, "system:file-import");
    }

    private void executeResign(ChangeItem item) {
        if (item.getTargetType() != TargetType.USER) {
            return;
        }
        User user = userRepository.findByUserId(item.getTargetKey())
            .orElseThrow(() -> new BizException("IMPORT_TARGET_NOT_FOUND", "用户不存在"));
        User resigned = user.toBuilder()
            .employmentStatus(EmploymentStatus.RESIGNED)
            .accountStatus("离职")
            .build();
        userRepository.save(resigned);
        try {
            ldapDirectoryService.disableUserIfExists(user.getUserId());
            ldapGroupService.removeUserFromAllGroupsIfExists(user.getUserId());
        } catch (RuntimeException exception) {
            throw new LdapImportException(resolveMessage(exception));
        }
    }

    private void syncUserLdapState(User user) {
        if (userAccessPolicy.canAuthenticate(user)) {
            ldapDirectoryService.enableUserIfExists(user.getUserId());
        } else {
            ldapDirectoryService.disableUserIfExists(user.getUserId());
        }
        List<String> groups = user.getPartTimeDeptCodes() == null
            ? List.of(user.getDeptCode())
            : new java.util.ArrayList<>(user.getPartTimeDeptCodes());
        if (user.getDeptCode() != null && !groups.contains(user.getDeptCode())) {
            groups.add(0, user.getDeptCode());
        }
        ldapGroupService.syncUserGroupsToExactState(
            user.getUserId(),
            groups.stream().filter(group -> group != null && !group.isBlank()).distinct().toList()
        );
    }

    private String resolveMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
    }

    private static class LdapImportException extends RuntimeException {
        private LdapImportException(String message) {
            super(message);
        }
    }
}
