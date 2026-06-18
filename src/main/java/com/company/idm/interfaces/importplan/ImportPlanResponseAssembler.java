package com.company.idm.interfaces.importplan;

import com.company.idm.application.sync.importplan.DepartmentImportSnapshot;
import com.company.idm.application.sync.importplan.ImportJsonService;
import com.company.idm.application.sync.importplan.UserImportSnapshot;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ChangeItemStatus;
import com.company.idm.domain.sync.ChangeType;
import com.company.idm.domain.sync.ImportBatch;
import com.company.idm.domain.sync.RollbackItem;
import com.company.idm.domain.sync.TargetType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImportPlanResponseAssembler {

    private final ImportJsonService jsonService;

    public ImportBatchDetailResponse toDetailResponse(ImportBatch batch) {
        return new ImportBatchDetailResponse(
            toBatchResponse(batch),
            batch.getChangeItems().stream().map(this::toChangeItemResponse).toList(),
            batch.getRollbackItems().stream().map(this::toRollbackItemResponse).toList()
        );
    }

    public ImportPlanReviewResponse toReviewResponse(ImportBatch batch) {
        List<ChangeItem> conflicts = batch.getChangeItems().stream()
            .filter(item -> item.getChangeType() == ChangeType.CONFLICT)
            .toList();
        List<UserReviewRowResponse> userRows = buildUserRows(batch);
        List<DepartmentReviewRowResponse> departmentRows = buildDepartmentRows(batch);
        List<ConflictReviewRowResponse> conflictRows = conflicts.stream()
            .map(item -> new ConflictReviewRowResponse(
                item.getId(),
                item.getTargetType().name(),
                item.getTargetKey(),
                item.getBlockReason(),
                item.getRiskLevel().name(),
                item.getStatus().name(),
                item.getErrorMessage()
            ))
            .toList();
        ImportReviewStatistics statistics = new ImportReviewStatistics(
            userRows.size(),
            departmentRows.size(),
            countRows(userRows, departmentRows, ChangeType.CREATE.name()),
            countRows(userRows, departmentRows, ChangeType.UPDATE.name()),
            countRows(userRows, departmentRows, ChangeType.RESIGN.name()),
            conflictRows.size(),
            countStatus(batch, ChangeItemStatus.FAILED),
            countStatus(batch, ChangeItemStatus.LDAP_FAILED)
        );
        return new ImportPlanReviewResponse(toBatchResponse(batch), statistics, userRows, departmentRows, conflictRows);
    }

    public ImportBatchResponse toBatchResponse(ImportBatch batch) {
        return new ImportBatchResponse(
            batch.getId(),
            batch.getBatchCode(),
            batch.getFileName(),
            batch.getFileHash(),
            batch.getSourceType().name(),
            batch.getTotalItems(),
            batch.getEnabledItems(),
            batch.getConflictItems(),
            batch.getStatus().name(),
            batch.getCreatedBy(),
            batch.getCreatedAt(),
            batch.getConfirmedBy(),
            batch.getConfirmedAt(),
            batch.getExecutedBy(),
            batch.getExecutedAt(),
            batch.getRollbackBy(),
            batch.getRollbackAt(),
            batch.getExpiredAt(),
            batch.getRemark()
        );
    }

    private ChangeItemResponse toChangeItemResponse(ChangeItem item) {
        return new ChangeItemResponse(
            item.getId(),
            item.getBatchId(),
            item.getTargetType().name(),
            item.getTargetKey(),
            item.getChangeType().name(),
            item.getFieldName(),
            item.getBeforeValue(),
            item.getAfterValue(),
            item.getBeforeJson(),
            item.getAfterJson(),
            item.getObjectVersion(),
            item.isDefaultEnabled(),
            item.isEnabled(),
            item.isRequiresConfirmation(),
            item.isConfirmed(),
            item.getRiskLevel().name(),
            item.getBlockReason(),
            item.getStatus().name(),
            item.getErrorMessage(),
            item.getRetryCount(),
            item.getExecutedAt()
        );
    }

    private RollbackItemResponse toRollbackItemResponse(RollbackItem item) {
        return new RollbackItemResponse(
            item.getId(),
            item.getBatchId(),
            item.getChangeItemId(),
            item.getTargetType().name(),
            item.getTargetKey(),
            item.getRollbackAction().name(),
            item.getRestoreJson(),
            item.getStatus().name(),
            item.getErrorMessage(),
            item.getExecutedAt()
        );
    }

    private List<UserReviewRowResponse> buildUserRows(ImportBatch batch) {
        Map<String, List<ChangeItem>> grouped = groupItems(batch, TargetType.USER);
        List<UserReviewRowResponse> rows = new ArrayList<>();
        for (Map.Entry<String, List<ChangeItem>> entry : grouped.entrySet()) {
            List<ChangeItem> items = entry.getValue();
            UserImportSnapshot snapshot = userSnapshot(items);
            rows.add(new UserReviewRowResponse(
                entry.getKey(),
                snapshot == null ? null : snapshot.realName(),
                snapshot == null ? null : snapshot.employeeNo(),
                snapshot == null ? null : snapshot.deptCode(),
                snapshot == null ? null : snapshot.jobTitle(),
                snapshot == null ? null : snapshot.leaderRef(),
                snapshot == null ? null : snapshot.directLeaderRaw(),
                snapshot == null || snapshot.status() == null ? null : snapshot.status().name(),
                snapshot == null || snapshot.employmentStatus() == null ? null : snapshot.employmentStatus().name(),
                rowChangeType(items),
                changeSummary(items),
                rowRiskLevel(items),
                items.stream().anyMatch(ChangeItem::isEnabled),
                items.stream().anyMatch(ChangeItem::isRequiresConfirmation),
                items.stream().allMatch(ChangeItem::isConfirmed),
                items.stream().map(ChangeItem::getId).toList(),
                items.stream().map(this::toFieldChange).toList()
            ));
        }
        return rows;
    }

    private List<DepartmentReviewRowResponse> buildDepartmentRows(ImportBatch batch) {
        Map<String, List<ChangeItem>> grouped = groupItems(batch, TargetType.DEPARTMENT);
        List<DepartmentReviewRowResponse> rows = new ArrayList<>();
        for (Map.Entry<String, List<ChangeItem>> entry : grouped.entrySet()) {
            List<ChangeItem> items = entry.getValue();
            DepartmentImportSnapshot snapshot = departmentSnapshot(items);
            rows.add(new DepartmentReviewRowResponse(
                entry.getKey(),
                snapshot == null ? null : snapshot.deptName(),
                snapshot == null ? null : snapshot.parentDeptCode(),
                snapshot == null || snapshot.status() == null ? null : String.valueOf(snapshot.status()),
                rowChangeType(items),
                changeSummary(items),
                rowRiskLevel(items),
                items.stream().anyMatch(ChangeItem::isEnabled),
                items.stream().anyMatch(ChangeItem::isRequiresConfirmation),
                items.stream().allMatch(ChangeItem::isConfirmed),
                items.stream().map(ChangeItem::getId).toList(),
                items.stream().map(this::toFieldChange).toList()
            ));
        }
        return rows;
    }

    private Map<String, List<ChangeItem>> groupItems(ImportBatch batch, TargetType targetType) {
        Map<String, List<ChangeItem>> result = new LinkedHashMap<>();
        batch.getChangeItems().stream()
            .filter(item -> item.getTargetType() == targetType)
            .filter(item -> item.getChangeType() != ChangeType.CONFLICT)
            .sorted(Comparator.comparing(ChangeItem::getId))
            .forEach(item -> result.computeIfAbsent(item.getTargetKey(), ignored -> new ArrayList<>()).add(item));
        return result;
    }

    private FieldChangeResponse toFieldChange(ChangeItem item) {
        return new FieldChangeResponse(
            item.getId(),
            item.getFieldName(),
            item.getBeforeValue(),
            item.getAfterValue(),
            item.getRiskLevel().name(),
            item.isEnabled(),
            item.isRequiresConfirmation(),
            item.isConfirmed(),
            item.getStatus().name(),
            item.getErrorMessage()
        );
    }

    private UserImportSnapshot userSnapshot(List<ChangeItem> items) {
        ChangeItem item = preferredSnapshotItem(items);
        if (item == null) {
            return null;
        }
        String json = item.getAfterJson() == null || item.getAfterJson().isBlank() ? item.getBeforeJson() : item.getAfterJson();
        return json == null ? null : jsonService.readUserSnapshot(json);
    }

    private DepartmentImportSnapshot departmentSnapshot(List<ChangeItem> items) {
        ChangeItem item = preferredSnapshotItem(items);
        if (item == null) {
            return null;
        }
        String json = item.getAfterJson() == null || item.getAfterJson().isBlank() ? item.getBeforeJson() : item.getAfterJson();
        return json == null ? null : jsonService.readDepartmentSnapshot(json);
    }

    private ChangeItem preferredSnapshotItem(List<ChangeItem> items) {
        return items.stream()
            .filter(item -> item.getAfterJson() != null && !item.getAfterJson().isBlank())
            .findFirst()
            .orElse(items.isEmpty() ? null : items.get(0));
    }

    private String rowChangeType(List<ChangeItem> items) {
        if (items.stream().anyMatch(item -> item.getChangeType() == ChangeType.RESIGN)) {
            return ChangeType.RESIGN.name();
        }
        if (items.stream().anyMatch(item -> item.getChangeType() == ChangeType.CREATE)) {
            return ChangeType.CREATE.name();
        }
        if (items.stream().anyMatch(item -> item.getChangeType() == ChangeType.DISABLE)) {
            return ChangeType.DISABLE.name();
        }
        return ChangeType.UPDATE.name();
    }

    private String rowRiskLevel(List<ChangeItem> items) {
        return items.stream()
            .map(item -> item.getRiskLevel().name())
            .max(Comparator.comparingInt(this::riskRank))
            .orElse("LOW");
    }

    private String changeSummary(List<ChangeItem> items) {
        String changeType = rowChangeType(items);
        if (!ChangeType.UPDATE.name().equals(changeType)) {
            return switch (changeType) {
                case "CREATE" -> "新增";
                case "RESIGN" -> "离职";
                case "DISABLE" -> "停用";
                default -> changeType;
            };
        }
        return "修改 " + items.stream()
            .map(ChangeItem::getFieldName)
            .filter(field -> field != null && !field.isBlank())
            .distinct()
            .toList();
    }

    private int riskRank(String riskLevel) {
        return switch (riskLevel) {
            case "BLOCKER" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            default -> 1;
        };
    }

    private int countRows(List<UserReviewRowResponse> userRows, List<DepartmentReviewRowResponse> departmentRows, String changeType) {
        return (int) userRows.stream().filter(row -> changeType.equals(row.changeType())).count()
            + (int) departmentRows.stream().filter(row -> changeType.equals(row.changeType())).count();
    }

    private int countStatus(ImportBatch batch, ChangeItemStatus status) {
        return (int) batch.getChangeItems().stream().filter(item -> item.getStatus() == status).count();
    }
}
