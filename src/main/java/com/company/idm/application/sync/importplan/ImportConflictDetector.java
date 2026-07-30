package com.company.idm.application.sync.importplan;

import com.company.idm.application.sync.feishu.FeishuDepartmentPayload;
import com.company.idm.application.sync.feishu.FeishuUserPayload;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ChangeItemStatus;
import com.company.idm.domain.sync.ChangeType;
import com.company.idm.domain.sync.ImportConflictCode;
import com.company.idm.domain.sync.RiskLevel;
import com.company.idm.domain.sync.TargetType;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImportConflictDetector {

    private final UserRepository userRepository;
    private final LdapDirectoryService ldapDirectoryService;
    private final LdapGroupService ldapGroupService;

    public ImportFileConflictIndex detectFileLevelConflicts(
        List<FeishuDepartmentPayload> departments,
        List<FeishuUserPayload> users,
        List<ChangeItem> output
    ) {
        Set<String> duplicateDepartmentExternalIds = duplicates(departments.stream()
            .map(FeishuDepartmentPayload::externalId)
            .toList());
        Set<String> duplicateDepartmentCodes = duplicates(departments.stream()
            .map(FeishuDepartmentPayload::departmentCode)
            .toList());
        Set<String> duplicateUserIds = duplicates(users.stream()
            .map(FeishuUserPayload::userId)
            .toList());
        Set<String> duplicateEmployeeNos = duplicates(users.stream()
            .map(FeishuUserPayload::employeeNo)
            .toList());

        duplicateDepartmentExternalIds.forEach(key ->
            output.add(blocker(TargetType.DEPARTMENT, key, ImportConflictCode.FILE_DUPLICATE_DEPARTMENT_EXTERNAL_ID,
                "同一个飞书部门 external_id 在导入文件中重复")));
        duplicateDepartmentCodes.forEach(key ->
            output.add(blocker(TargetType.DEPARTMENT, key, ImportConflictCode.FILE_DUPLICATE_DEPARTMENT_CODE,
                "同一个部门编码在导入文件中重复")));
        duplicateUserIds.forEach(key ->
            output.add(blocker(TargetType.USER, key, ImportConflictCode.FILE_DUPLICATE_USER_ID,
                "同一个飞书 user_id 在导入文件中重复")));
        duplicateEmployeeNos.forEach(key ->
            output.add(blocker(TargetType.USER, key, ImportConflictCode.FILE_DUPLICATE_EMPLOYEE_NO,
                "同一个工号在导入文件中重复")));

        return new ImportFileConflictIndex(
            duplicateDepartmentExternalIds,
            duplicateDepartmentCodes,
            duplicateUserIds,
            duplicateEmployeeNos
        );
    }

    public void detectDepartmentConflict(FeishuDepartmentPayload payload, Department existingByExternalId, Department existingByCode, List<ChangeItem> output) {
        if (existingByExternalId != null && !existingByExternalId.getDeptCode().equals(payload.departmentCode())) {
            output.add(blocker(TargetType.DEPARTMENT, payload.departmentCode(),
                ImportConflictCode.DEPARTMENT_EXTERNAL_ID_CODE_MISMATCH,
                "飞书部门 external_id 与 dept_code 映射冲突"));
        }
        if (existingByExternalId == null
            && existingByCode != null
            && existingByCode.getExternalId() != null
            && !existingByCode.getExternalId().equals(payload.externalId())) {
            output.add(blocker(TargetType.DEPARTMENT, payload.departmentCode(),
                ImportConflictCode.DEPARTMENT_CODE_OWNED_BY_ANOTHER_DEPARTMENT,
                "部门编码已被其他飞书部门占用"));
        }
        if (ldapGroupService.existsGroup(payload.departmentCode())
            && (existingByCode == null || !payload.departmentCode().equals(existingByCode.getDeptCode()))) {
            output.add(blocker(TargetType.LDAP_GROUP, payload.departmentCode(), ImportConflictCode.LDAP_GROUP_DN_OCCUPIED,
                "LDAP group DN 将与已有非本部门条目冲突"));
        }
    }

    public void detectUserConflict(FeishuUserPayload payload, User existingByUserId, User existingByEmployeeNo, List<ChangeItem> output) {
        if (existingByUserId != null
            && existingByEmployeeNo != null
            && !existingByUserId.getId().equals(existingByEmployeeNo.getId())) {
            output.add(blocker(TargetType.USER, payload.userId(),
                ImportConflictCode.USER_ID_EMPLOYEE_NO_MATCH_DIFFERENT_USERS,
                "飞书 user_id 匹配到用户 A，但工号匹配到用户 B"));
        }
        if (existingByUserId == null && existingByEmployeeNo != null) {
            output.add(blocker(TargetType.USER, payload.userId(),
                ImportConflictCode.EMPLOYEE_NO_OWNED_BY_ANOTHER_USER,
                "飞书用户工号已属于其他平台用户"));
        }
        if (existingByUserId == null && ldapDirectoryService.existsByUid(payload.userId())) {
            output.add(blocker(TargetType.LDAP_USER, payload.userId(), ImportConflictCode.LDAP_UID_OCCUPIED,
                "LDAP uid 将与已有非本用户条目冲突"));
        }
    }

    private Set<String> duplicates(List<String> values) {
        Map<String, Integer> counts = new HashMap<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            counts.merge(value.trim(), 1, Integer::sum);
        }
        Set<String> result = new LinkedHashSet<>();
        counts.forEach((key, count) -> {
            if (count > 1) {
                result.add(key);
            }
        });
        return result;
    }

    private ChangeItem blocker(
        TargetType targetType,
        String targetKey,
        ImportConflictCode conflictCode,
        String reason
    ) {
        return ChangeItem.builder()
            .targetType(targetType)
            .targetKey(targetKey)
            .changeType(ChangeType.CONFLICT)
            .defaultEnabled(false)
            .enabled(false)
            .requiresConfirmation(true)
            .confirmed(false)
            .riskLevel(RiskLevel.BLOCKER)
            .conflictCode(conflictCode)
            .blockReason(reason)
            .status(ChangeItemStatus.PENDING)
            .retryCount(0)
            .build();
    }
}
