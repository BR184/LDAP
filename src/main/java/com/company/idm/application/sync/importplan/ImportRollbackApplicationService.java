package com.company.idm.application.sync.importplan;

import com.company.idm.application.sync.LeaderRoleDerivationService;
import com.company.idm.application.user.InitialPasswordPolicy;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ChangeType;
import com.company.idm.domain.sync.ImportBatch;
import com.company.idm.domain.sync.ImportBatchRepository;
import com.company.idm.domain.sync.RollbackAction;
import com.company.idm.domain.sync.RollbackItem;
import com.company.idm.domain.sync.RollbackItemStatus;
import com.company.idm.domain.sync.TargetType;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ImportRollbackApplicationService {

    private final ImportBatchRepository importBatchRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final LdapDirectoryService ldapDirectoryService;
    private final LdapGroupService ldapGroupService;
    private final LeaderRoleDerivationService leaderRoleDerivationService;
    private final ImportJsonService jsonService;
    private final InitialPasswordPolicy initialPasswordPolicy;

    @Transactional
    public ImportBatch generateRollbackPlan(Long batchId) {
        ImportBatch batch = load(batchId);
        if (!batch.canRollback()) {
            throw new BizException("IMPORT_BATCH_CANNOT_ROLLBACK", "当前批次不允许撤回");
        }
        if (batch.getRollbackItems() != null && batch.getRollbackItems().stream()
            .anyMatch(item -> item.getStatus() == RollbackItemStatus.PENDING)) {
            return batch;
        }
        List<RollbackItem> rollbackItems = new ArrayList<>();
        for (ChangeItem item : batch.getExecutedChangeItems()) {
            rollbackItems.add(generateRollbackItem(item));
        }
        ImportBatch planned = batch.toBuilder().rollbackItems(rollbackItems).build();
        return importBatchRepository.save(planned);
    }

    @Transactional
    public ImportBatch executeRollback(Long batchId, String rollbackBy) {
        ImportBatch batch = generateRollbackPlan(batchId);
        for (RollbackItem item : batch.getRollbackItems()) {
            try {
                executeRollbackItem(item);
                item.markExecuted();
                markOriginalChangeRolledBack(batch, item.getChangeItemId());
            } catch (RuntimeException exception) {
                item.markFailed(resolveMessage(exception));
            }
        }
        batch.markRolledBack(rollbackBy);
        ImportBatch saved = importBatchRepository.save(batch);
        leaderRoleDerivationService.syncDerivedRoles();
        return saved;
    }

    private RollbackItem generateRollbackItem(ChangeItem item) {
        RollbackAction action;
        String restoreJson = item.getBeforeJson();
        if (item.getChangeType() == ChangeType.CREATE) {
            action = item.getTargetType() == TargetType.USER ? RollbackAction.DISABLE_CREATED : RollbackAction.RESTORE_STATUS;
            restoreJson = item.getAfterJson();
        } else if (item.getTargetType() == TargetType.USER || item.getTargetType() == TargetType.DEPARTMENT) {
            action = RollbackAction.RESTORE_FIELDS;
        } else {
            action = RollbackAction.RESTORE_LDAP;
        }
        return RollbackItem.builder()
            .batchId(item.getBatchId())
            .changeItemId(item.getId())
            .targetType(item.getTargetType())
            .targetKey(item.getTargetKey())
            .rollbackAction(action)
            .restoreJson(restoreJson)
            .status(RollbackItemStatus.PENDING)
            .build();
    }

    private void executeRollbackItem(RollbackItem item) {
        if (item.getRollbackAction() == RollbackAction.DISABLE_CREATED) {
            disableCreatedUser(item.getTargetKey());
            return;
        }
        if (item.getRollbackAction() == RollbackAction.RESTORE_FIELDS) {
            restoreFields(item);
            return;
        }
        if (item.getRollbackAction() == RollbackAction.RESTORE_STATUS) {
            disableCreatedDepartment(item);
        }
    }

    private void restoreFields(RollbackItem item) {
        if (item.getTargetType() == TargetType.USER) {
            User snapshot = jsonService.readUserSnapshot(item.getRestoreJson()).toUser();
            User saved = userRepository.save(snapshot);
            ldapDirectoryService.createOrUpdateUser(saved, initialPasswordPolicy.resolve(saved.getMobile()));
            if (saved.getStatus() == UserStatus.ENABLED) {
                ldapDirectoryService.enableUserIfExists(saved.getUserId());
            } else {
                ldapDirectoryService.disableUserIfExists(saved.getUserId());
            }
            return;
        }
        if (item.getTargetType() == TargetType.DEPARTMENT) {
            Department snapshot = jsonService.readDepartmentSnapshot(item.getRestoreJson()).toDepartment();
            Department saved = departmentRepository.save(snapshot);
            ldapGroupService.createGroup(saved.getDeptCode(), saved.getDeptName());
        }
    }

    private void disableCreatedUser(String userId) {
        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new BizException("IMPORT_TARGET_NOT_FOUND", "用户不存在"));
        userRepository.save(user.toBuilder().status(UserStatus.DISABLED).build());
        ldapDirectoryService.disableUserIfExists(userId);
        ldapGroupService.removeUserFromAllGroupsIfExists(userId);
    }

    private void disableCreatedDepartment(RollbackItem item) {
        Department snapshot = jsonService.readDepartmentSnapshot(item.getRestoreJson()).toDepartment();
        Department existing = departmentRepository.findByDeptCode(snapshot.getDeptCode())
            .orElseThrow(() -> new BizException("IMPORT_TARGET_NOT_FOUND", "部门不存在"));
        departmentRepository.save(existing.toBuilder().status(0).build());
    }

    private void markOriginalChangeRolledBack(ImportBatch batch, Long changeItemId) {
        batch.getChangeItems().stream()
            .filter(item -> item.getId().equals(changeItemId))
            .findFirst()
            .ifPresent(ChangeItem::markRolledBack);
    }

    private ImportBatch load(Long batchId) {
        return importBatchRepository.findById(batchId)
            .orElseThrow(() -> new BizException("IMPORT_BATCH_NOT_FOUND", "导入批次不存在"));
    }

    private String resolveMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
    }
}
