package com.company.idm.interfaces.importplan;

import com.company.idm.application.sync.importplan.DepartmentImportSnapshot;
import com.company.idm.application.sync.importplan.ImportJsonService;
import com.company.idm.application.sync.importplan.ImportReviewDisplayService;
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
    private final ImportReviewDisplayService importReviewDisplayService;

    public ImportBatchDetailResponse toDetailResponse(ImportBatch batch) {
        ImportReviewDisplayService.ReviewDirectory directory = importReviewDisplayService.buildDirectory(batch);
        return new ImportBatchDetailResponse(
            toBatchResponse(batch),
            batch.getChangeItems().stream().map(item -> toChangeItemResponse(item, directory)).toList(),
            batch.getRollbackItems().stream().map(this::toRollbackItemResponse).toList()
        );
    }

    public ImportPlanReviewResponse toReviewResponse(ImportBatch batch) {
        ImportReviewDisplayService.ReviewDirectory directory = importReviewDisplayService.buildDirectory(batch);
        List<ChangeItem> conflicts = batch.getChangeItems().stream()
            .filter(item -> item.getChangeType() == ChangeType.CONFLICT)
            .toList();
        List<UserReviewRowResponse> userRows = buildUserRows(batch, directory);
        List<DepartmentReviewRowResponse> departmentRows = buildDepartmentRows(batch, directory);
        List<ConflictReviewRowResponse> conflictRows = conflicts.stream()
            .map(this::toConflictReviewRow)
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
            batch.getCancelledBy(),
            batch.getCancelledAt(),
            batch.getRemark()
        );
    }

    private ChangeItemResponse toChangeItemResponse(ChangeItem item, ImportReviewDisplayService.ReviewDirectory directory) {
        ImportReviewDisplayService.FormattedField display = importReviewDisplayService.formatField(item, directory);
        return new ChangeItemResponse(
            item.getId(),
            item.getBatchId(),
            item.getTargetType().name(),
            item.getTargetKey(),
            item.getChangeType().name(),
            display.fieldKey() == null ? null : display.fieldKey().name(),
            display.fieldLabel(),
            display.beforeValue(),
            display.afterValue(),
            item.getObjectVersion(),
            item.isDefaultEnabled(),
            item.isEnabled(),
            item.isRequiresConfirmation(),
            item.isConfirmed(),
            item.getRiskLevel().name(),
            item.getBlockReason(),
            item.getConflictCode() == null ? null : item.getConflictCode().name(),
            item.availableResolutionActions().stream().map(Enum::name).toList(),
            item.getStatus().name(),
            item.getErrorMessage(),
            item.getRetryCount(),
            item.getExecutedAt(),
            item.getResolutionAction() == null ? null : item.getResolutionAction().name(),
            item.getResolvedBy(),
            item.getResolvedAt()
        );
    }

    private ConflictReviewRowResponse toConflictReviewRow(ChangeItem item) {
        UserImportSnapshot before = conflictUserSnapshot(item.getTargetType(), item.getBeforeJson());
        UserImportSnapshot after = conflictUserSnapshot(item.getTargetType(), item.getAfterJson());
        return new ConflictReviewRowResponse(
            item.getId(),
            item.getTargetType().name(),
            item.getTargetKey(),
            after == null ? null : after.realName(),
            after == null ? (before == null ? null : before.employeeNo()) : after.employeeNo(),
            before == null ? null : before.userId(),
            before == null ? null : before.realName(),
            item.getBlockReason(),
            item.getConflictCode() == null ? null : item.getConflictCode().name(),
            item.availableResolutionActions().stream().map(Enum::name).toList(),
            item.getRiskLevel().name(),
            item.getStatus().name(),
            item.getErrorMessage(),
            item.getResolutionAction() == null ? null : item.getResolutionAction().name(),
            item.getResolvedBy(),
            item.getResolvedAt()
        );
    }

    private UserImportSnapshot conflictUserSnapshot(TargetType targetType, String json) {
        if ((targetType != TargetType.USER && targetType != TargetType.LDAP_USER)
            || json == null
            || json.isBlank()) {
            return null;
        }
        return jsonService.readUserSnapshot(json);
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

    private List<UserReviewRowResponse> buildUserRows(
        ImportBatch batch,
        ImportReviewDisplayService.ReviewDirectory directory
    ) {
        Map<String, List<ChangeItem>> grouped = groupItems(batch, TargetType.USER);
        List<UserReviewRowResponse> rows = new ArrayList<>();
        for (Map.Entry<String, List<ChangeItem>> entry : grouped.entrySet()) {
            List<ChangeItem> items = entry.getValue();
            UserImportSnapshot snapshot = userSnapshot(items);
            ImportReviewDisplayService.DepartmentPresentation department = importReviewDisplayService.department(
                snapshot == null ? null : snapshot.deptCode(),
                directory
            );
            rows.add(new UserReviewRowResponse(
                entry.getKey(),
                snapshot == null ? null : snapshot.realName(),
                snapshot == null ? null : snapshot.employeeNo(),
                department.departmentName(),
                department.departmentPath(),
                snapshot == null ? null : snapshot.jobTitle(),
                snapshot == null ? null : snapshot.leaderRef(),
                snapshot == null ? null : snapshot.directLeaderRaw(),
                snapshot == null || snapshot.employmentStatus() == null ? null : snapshot.employmentStatus().name(),
                rowChangeType(items),
                changeSummary(items),
                rowRiskLevel(items),
                items.stream().anyMatch(ChangeItem::isEnabled),
                items.stream().anyMatch(ChangeItem::isRequiresConfirmation),
                items.stream().allMatch(ChangeItem::isConfirmed),
                items.stream().map(ChangeItem::getId).toList(),
                items.stream().map(item -> toFieldChange(item, directory)).toList()
            ));
        }
        return rows;
    }

    private List<DepartmentReviewRowResponse> buildDepartmentRows(
        ImportBatch batch,
        ImportReviewDisplayService.ReviewDirectory directory
    ) {
        Map<String, List<ChangeItem>> grouped = groupItems(batch, TargetType.DEPARTMENT);
        List<DepartmentReviewRowResponse> rows = new ArrayList<>();
        for (Map.Entry<String, List<ChangeItem>> entry : grouped.entrySet()) {
            List<ChangeItem> items = entry.getValue();
            DepartmentImportSnapshot snapshot = departmentSnapshot(items);
            ImportReviewDisplayService.DepartmentPresentation parentDepartment = importReviewDisplayService.department(
                snapshot == null ? null : snapshot.parentDeptCode(),
                directory
            );
            ImportReviewDisplayService.DepartmentPresentation department = importReviewDisplayService.department(
                snapshot == null ? null : snapshot.deptCode(),
                directory
            );
            rows.add(new DepartmentReviewRowResponse(
                entry.getKey(),
                snapshot == null ? null : snapshot.deptName(),
                parentDepartment.departmentName(),
                department.departmentPath(),
                snapshot == null || snapshot.status() == null ? null : String.valueOf(snapshot.status()),
                rowChangeType(items),
                changeSummary(items),
                rowRiskLevel(items),
                items.stream().anyMatch(ChangeItem::isEnabled),
                items.stream().anyMatch(ChangeItem::isRequiresConfirmation),
                items.stream().allMatch(ChangeItem::isConfirmed),
                items.stream().map(ChangeItem::getId).toList(),
                items.stream().map(item -> toFieldChange(item, directory)).toList()
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

    private FieldChangeResponse toFieldChange(ChangeItem item, ImportReviewDisplayService.ReviewDirectory directory) {
        ImportReviewDisplayService.FormattedField display = importReviewDisplayService.formatField(item, directory);
        return new FieldChangeResponse(
            item.getId(),
            display.fieldKey() == null ? null : display.fieldKey().name(),
            display.fieldLabel(),
            display.beforeValue(),
            display.afterValue(),
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
            .map(ChangeItem::getFieldKey)
            .filter(java.util.Objects::nonNull)
            .map(com.company.idm.domain.sync.ImportFieldKey::getLabel)
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
