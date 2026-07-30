package com.company.idm.application.sync.importplan;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ImportBatch;
import com.company.idm.domain.sync.ImportConflictCode;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImportEmployeeNumberMergeService {

    private final UserRepository userRepository;
    private final ImportDiffPolicyService diffPolicyService;
    private final ImportConflictImpactResolver impactResolver;
    private final ImportConflictCandidateRecoveryService candidateRecoveryService;
    private final ImportJsonService jsonService;

    public MergePreparation prepare(ImportBatch batch, ChangeItem conflict, List<ChangeItem> allItems) {
        if (conflict == null || conflict.getConflictCode() != ImportConflictCode.EMPLOYEE_NO_OWNED_BY_ANOTHER_USER) {
            throw new BizException("IMPORT_CONFLICT_MERGE_NOT_ALLOWED", "当前冲突不允许按工号合并更新");
        }
        candidateRecoveryService.restoreIfMissing(batch, conflict);
        UserImportSnapshot incoming = jsonService.readUserSnapshot(conflict.getAfterJson());
        if (incoming == null || incoming.employeeNo() == null || incoming.employeeNo().isBlank()) {
            throw new BizException("IMPORT_CONFLICT_CANDIDATE_MISSING", "冲突缺少可合并的员工信息");
        }
        User existing = userRepository.findByEmployeeNo(incoming.employeeNo())
            .orElseThrow(() -> new BizException("IMPORT_CONFLICT_TARGET_NOT_FOUND", "工号对应的现有账号不存在"));
        User target = incoming.applyTo(existing, existing.getIntranetEmail());
        List<ChangeItem> updates = diffPolicyService.buildUserUpdateItems(
            UserImportSnapshot.from(existing),
            UserImportSnapshot.from(target)
        );
        return new MergePreparation(
            impactResolver.resolveRelatedItemIds(conflict, allItems),
            updates
        );
    }

    public record MergePreparation(List<Long> relatedItemIds, List<ChangeItem> updateItems) {
        public MergePreparation {
            relatedItemIds = relatedItemIds == null ? List.of() : List.copyOf(relatedItemIds);
            updateItems = updateItems == null ? List.of() : List.copyOf(updateItems);
        }
    }
}
