package com.company.idm.application.sync.importplan;

import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ChangeItemStatus;
import com.company.idm.domain.sync.ChangeType;
import com.company.idm.domain.sync.ImportFieldKey;
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
        addDepartmentField(items, before, after, ImportFieldKey.DEPARTMENT_NAME, before.deptName(), after.deptName(), RiskLevel.LOW);
        addDepartmentField(items, before, after, ImportFieldKey.DEPARTMENT_PARENT, before.parentDeptCode(), after.parentDeptCode(), RiskLevel.HIGH);
        addDepartmentField(items, before, after, ImportFieldKey.DEPARTMENT_PATH, before.ancestorPath(), after.ancestorPath(), RiskLevel.HIGH);
        addDepartmentField(items, before, after, ImportFieldKey.DEPARTMENT_LEVEL, before.deptLevel(), after.deptLevel(), RiskLevel.HIGH);
        addDepartmentField(items, before, after, ImportFieldKey.DEPARTMENT_STATUS, before.status(), after.status(), RiskLevel.MEDIUM);
        return items;
    }

    public List<ChangeItem> buildUserUpdateItems(UserImportSnapshot before, UserImportSnapshot after) {
        List<ChangeItem> items = new ArrayList<>();
        addUserField(items, before, after, ImportFieldKey.USER_REAL_NAME, before.realName(), after.realName(), RiskLevel.LOW);
        addUserField(items, before, after, ImportFieldKey.USER_EMAIL, before.email(), after.email(), RiskLevel.LOW);
        addUserField(items, before, after, ImportFieldKey.USER_MOBILE, before.mobile(), after.mobile(), RiskLevel.LOW);
        addUserField(items, before, after, ImportFieldKey.USER_EMPLOYEE_NO, before.employeeNo(), after.employeeNo(), RiskLevel.HIGH);
        addUserField(items, before, after, ImportFieldKey.USER_MAIN_DEPARTMENT, before.deptCode(), after.deptCode(), RiskLevel.MEDIUM);
        addUserField(items, before, after, ImportFieldKey.USER_JOB_TITLE, before.jobTitle(), after.jobTitle(), RiskLevel.LOW);
        addUserField(items, before, after, ImportFieldKey.USER_DIRECT_LEADER, before.directLeaderRaw(), after.directLeaderRaw(), RiskLevel.LOW);
        addUserField(items, before, after, ImportFieldKey.USER_LEADER_REFERENCE, before.leaderRef(), after.leaderRef(), RiskLevel.MEDIUM);
        addUserField(items, before, after, ImportFieldKey.USER_ACCOUNT_STATUS, before.accountStatus(), after.accountStatus(), RiskLevel.LOW);
        addUserField(items, before, after, ImportFieldKey.USER_PART_TIME_DEPARTMENTS, before.partTimeDeptCodes(), after.partTimeDeptCodes(), RiskLevel.MEDIUM);
        addUserField(items, before, after, ImportFieldKey.USER_EMPLOYMENT_STATUS, before.employmentStatus(), after.employmentStatus(), RiskLevel.HIGH);
        return items;
    }

    private void addDepartmentField(
        List<ChangeItem> items,
        DepartmentImportSnapshot before,
        DepartmentImportSnapshot after,
        ImportFieldKey fieldKey,
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
            fieldKey,
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
        ImportFieldKey fieldKey,
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
            fieldKey,
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
        ImportFieldKey fieldKey,
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
            .fieldKey(fieldKey)
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
