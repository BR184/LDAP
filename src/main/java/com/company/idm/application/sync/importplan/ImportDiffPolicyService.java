package com.company.idm.application.sync.importplan;

import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ChangeItemStatus;
import com.company.idm.domain.sync.ChangeType;
import com.company.idm.domain.sync.RiskLevel;
import com.company.idm.domain.sync.TargetType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImportDiffPolicyService {

    private final ImportJsonService jsonService;

    public List<ChangeItem> buildDepartmentUpdateItems(
        DepartmentImportSnapshot before,
        DepartmentImportSnapshot after
    ) {
        List<ChangeItem> items = new ArrayList<>();
        addDepartmentField(items, before, after, "deptName", before.deptName(), after.deptName(), RiskLevel.LOW);
        addDepartmentField(items, before, after, "parentDeptCode", before.parentDeptCode(), after.parentDeptCode(), RiskLevel.HIGH);
        addDepartmentField(items, before, after, "ancestorPath", before.ancestorPath(), after.ancestorPath(), RiskLevel.HIGH);
        addDepartmentField(items, before, after, "deptLevel", before.deptLevel(), after.deptLevel(), RiskLevel.HIGH);
        addDepartmentField(items, before, after, "status", before.status(), after.status(), RiskLevel.MEDIUM);
        return items;
    }

    public List<ChangeItem> buildUserUpdateItems(UserImportSnapshot before, UserImportSnapshot after) {
        List<ChangeItem> items = new ArrayList<>();
        addUserField(items, before, after, "realName", before.realName(), after.realName(), RiskLevel.LOW);
        addUserField(items, before, after, "email", before.email(), after.email(), RiskLevel.LOW);
        addUserField(items, before, after, "mobile", before.mobile(), after.mobile(), RiskLevel.LOW);
        addUserField(items, before, after, "employeeNo", before.employeeNo(), after.employeeNo(), RiskLevel.HIGH);
        addUserField(items, before, after, "deptCode", before.deptCode(), after.deptCode(), RiskLevel.MEDIUM);
        addUserField(items, before, after, "jobTitle", before.jobTitle(), after.jobTitle(), RiskLevel.LOW);
        addUserField(items, before, after, "directLeaderRaw", before.directLeaderRaw(), after.directLeaderRaw(), RiskLevel.LOW);
        addUserField(items, before, after, "leaderRef", before.leaderRef(), after.leaderRef(), RiskLevel.MEDIUM);
        addUserField(items, before, after, "accountStatus", before.accountStatus(), after.accountStatus(), RiskLevel.LOW);
        addUserField(items, before, after, "partTimeDeptCodes", before.partTimeDeptCodes(), after.partTimeDeptCodes(), RiskLevel.MEDIUM);
        addUserField(items, before, after, "status", before.status(), after.status(), RiskLevel.MEDIUM);
        addUserField(items, before, after, "employmentStatus", before.employmentStatus(), after.employmentStatus(), RiskLevel.HIGH);
        return items;
    }

    private void addDepartmentField(
        List<ChangeItem> items,
        DepartmentImportSnapshot before,
        DepartmentImportSnapshot after,
        String fieldName,
        Object beforeValue,
        Object afterValue,
        RiskLevel riskLevel
    ) {
        if (Objects.equals(beforeValue, afterValue)) {
            return;
        }
        items.add(baseUpdateItem(
            TargetType.DEPARTMENT,
            after.deptCode(),
            fieldName,
            beforeValue,
            afterValue,
            jsonService.toJson(before),
            jsonService.toJson(after),
            riskLevel
        ));
    }

    private void addUserField(
        List<ChangeItem> items,
        UserImportSnapshot before,
        UserImportSnapshot after,
        String fieldName,
        Object beforeValue,
        Object afterValue,
        RiskLevel riskLevel
    ) {
        if (Objects.equals(beforeValue, afterValue)) {
            return;
        }
        items.add(baseUpdateItem(
            TargetType.USER,
            after.userId(),
            fieldName,
            beforeValue,
            afterValue,
            jsonService.toJson(before),
            jsonService.toJson(after),
            riskLevel
        ));
    }

    private ChangeItem baseUpdateItem(
        TargetType targetType,
        String targetKey,
        String fieldName,
        Object beforeValue,
        Object afterValue,
        String beforeJson,
        String afterJson,
        RiskLevel riskLevel
    ) {
        return ChangeItem.builder()
            .targetType(targetType)
            .targetKey(targetKey)
            .changeType(ChangeType.UPDATE)
            .fieldName(fieldName)
            .beforeValue(valueToString(beforeValue))
            .afterValue(valueToString(afterValue))
            .beforeJson(beforeJson)
            .afterJson(afterJson)
            .defaultEnabled(riskLevel != RiskLevel.BLOCKER)
            .enabled(riskLevel != RiskLevel.BLOCKER)
            .requiresConfirmation(riskLevel == RiskLevel.HIGH)
            .confirmed(riskLevel != RiskLevel.HIGH)
            .riskLevel(riskLevel)
            .status(ChangeItemStatus.PENDING)
            .retryCount(0)
            .build();
    }

    private String valueToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Iterable<?>) {
            return jsonService.toJson(value);
        }
        return String.valueOf(value);
    }
}
