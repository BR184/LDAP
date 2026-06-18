package com.company.idm.application.sync.importplan;

import com.company.idm.application.sync.feishu.FeishuDepartmentPayload;
import com.company.idm.application.sync.feishu.FeishuUserPayload;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ChangeItemStatus;
import com.company.idm.domain.sync.ChangeType;
import com.company.idm.domain.sync.RiskLevel;
import com.company.idm.domain.sync.TargetType;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImportConflictDetector {

    private final UserRepository userRepository;
    private final LdapDirectoryService ldapDirectoryService;
    private final LdapGroupService ldapGroupService;

    public void detectFileLevelConflicts(
        List<FeishuDepartmentPayload> departments,
        List<FeishuUserPayload> users,
        List<ChangeItem> output
    ) {
        countDepartments(departments, output);
        countUsers(users, output);
    }

    public void detectDepartmentConflict(FeishuDepartmentPayload payload, Department existingByExternalId, Department existingByCode, List<ChangeItem> output) {
        if (existingByExternalId != null && !existingByExternalId.getDeptCode().equals(payload.departmentCode())) {
            output.add(blocker(TargetType.DEPARTMENT, payload.departmentCode(), "飞书部门 external_id 与 dept_code 映射冲突"));
        }
        if (existingByExternalId == null
            && existingByCode != null
            && existingByCode.getExternalId() != null
            && !existingByCode.getExternalId().equals(payload.externalId())) {
            output.add(blocker(TargetType.DEPARTMENT, payload.departmentCode(), "部门编码已被其他飞书部门占用"));
        }
        if (ldapGroupService.existsGroup(payload.departmentCode())
            && (existingByCode == null || !payload.departmentCode().equals(existingByCode.getDeptCode()))) {
            output.add(blocker(TargetType.LDAP_GROUP, payload.departmentCode(), "LDAP group DN 将与已有非本部门条目冲突"));
        }
    }

    public void detectUserConflict(FeishuUserPayload payload, User existingByUserId, User existingByEmployeeNo, List<ChangeItem> output) {
        if (existingByUserId != null
            && existingByEmployeeNo != null
            && !existingByUserId.getId().equals(existingByEmployeeNo.getId())) {
            output.add(blocker(TargetType.USER, payload.userId(), "飞书 user_id 匹配到用户 A，但工号匹配到用户 B"));
        }
        if (existingByUserId == null && existingByEmployeeNo != null) {
            output.add(blocker(TargetType.USER, payload.userId(), "飞书用户工号已属于其他平台用户"));
        }
        if (existingByUserId == null && ldapDirectoryService.existsByUid(payload.userId())) {
            output.add(blocker(TargetType.LDAP_USER, payload.userId(), "LDAP uid 将与已有非本用户条目冲突"));
        }
    }

    private void countDepartments(List<FeishuDepartmentPayload> departments, List<ChangeItem> output) {
        Map<String, Integer> externalIds = new HashMap<>();
        Map<String, Integer> deptCodes = new HashMap<>();
        for (FeishuDepartmentPayload payload : departments) {
            externalIds.merge(payload.externalId(), 1, Integer::sum);
            deptCodes.merge(payload.departmentCode(), 1, Integer::sum);
        }
        externalIds.forEach((key, count) -> {
            if (count > 1) {
                output.add(blocker(TargetType.DEPARTMENT, key, "同一个飞书部门 external_id 在导入文件中重复"));
            }
        });
        deptCodes.forEach((key, count) -> {
            if (count > 1) {
                output.add(blocker(TargetType.DEPARTMENT, key, "同一个部门编码在导入文件中重复"));
            }
        });
    }

    private void countUsers(List<FeishuUserPayload> users, List<ChangeItem> output) {
        Map<String, Integer> userIds = new HashMap<>();
        Map<String, Integer> employeeNos = new HashMap<>();
        for (FeishuUserPayload payload : users) {
            userIds.merge(payload.userId(), 1, Integer::sum);
            employeeNos.merge(payload.employeeNo(), 1, Integer::sum);
        }
        userIds.forEach((key, count) -> {
            if (count > 1) {
                output.add(blocker(TargetType.USER, key, "同一个飞书 user_id 在导入文件中重复"));
            }
        });
        employeeNos.forEach((key, count) -> {
            if (count > 1) {
                output.add(blocker(TargetType.USER, key, "同一个工号在导入文件中重复"));
            }
        });
    }

    private ChangeItem blocker(TargetType targetType, String targetKey, String reason) {
        return ChangeItem.builder()
            .targetType(targetType)
            .targetKey(targetKey)
            .changeType(ChangeType.CONFLICT)
            .defaultEnabled(false)
            .enabled(false)
            .requiresConfirmation(true)
            .confirmed(false)
            .riskLevel(RiskLevel.BLOCKER)
            .blockReason(reason)
            .status(ChangeItemStatus.PENDING)
            .retryCount(0)
            .build();
    }
}
