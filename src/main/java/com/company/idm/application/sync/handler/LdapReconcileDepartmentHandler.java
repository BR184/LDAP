package com.company.idm.application.sync.handler;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.SyncJobExecutionResult;
import com.company.idm.application.sync.SyncJobHandler;
import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncJobType;
import com.company.idm.common.enums.SyncRunStatus;
import com.company.idm.common.enums.SyncTargetType;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapGroupService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 提供部门维度的 LDAP 对账处理器。
 */
@Component
public class LdapReconcileDepartmentHandler implements SyncJobHandler {

    private final DepartmentRepository departmentRepository;
    private final LdapGroupService ldapGroupService;

    public LdapReconcileDepartmentHandler(DepartmentRepository departmentRepository, LdapGroupService ldapGroupService) {
        this.departmentRepository = departmentRepository;
        this.ldapGroupService = ldapGroupService;
    }

    @Override
    public SyncJobType jobType() {
        return SyncJobType.LDAP_RECONCILE_DEPARTMENT;
    }

    @Override
    public SyncJobExecutionResult preview(SyncRequestPayload payload) {
        return buildResult(scanDiffs(false), false);
    }

    @Override
    public SyncJobExecutionResult execute(SyncRequestPayload payload) {
        return buildResult(scanDiffs(payload.autoRepair()), payload.autoRepair());
    }

    private List<SyncDiffPayload> scanDiffs(boolean autoRepair) {
        List<SyncDiffPayload> diffs = new ArrayList<>();
        for (Department department : departmentRepository.findAll()) {
            boolean ldapExists = ldapGroupService.existsGroup(department.getDeptCode());
            if (!ldapExists) {
                if (autoRepair) {
                    String ldapDn = ldapGroupService.createGroup(department.getDeptCode(), department.getDeptName());
                    departmentRepository.save(department.toBuilder().ldapDn(ldapDn).build());
                    continue;
                }
                diffs.add(new SyncDiffPayload(
                    SyncTargetType.DEPARTMENT,
                    department.getDeptCode(),
                    SyncDiffType.MISSING_IN_LDAP,
                    snapshotDepartment(department),
                    null,
                    true
                ));
                continue;
            }
            String currentDn = ldapGroupService.findGroupDn(department.getDeptCode());
            if (department.getLdapDn() == null || !department.getLdapDn().equals(currentDn)) {
                if (autoRepair) {
                    String repairedDn = ldapGroupService.updateGroup(department.getDeptCode(), department.getDeptName());
                    departmentRepository.save(department.toBuilder().ldapDn(repairedDn).build());
                    continue;
                }
                diffs.add(new SyncDiffPayload(
                    SyncTargetType.DEPARTMENT,
                    department.getDeptCode(),
                    SyncDiffType.FIELD_MISMATCH,
                    snapshotDepartment(department),
                    "{\"ldapDn\":\"" + safe(currentDn) + "\"}",
                    true
                ));
            }
        }
        return diffs;
    }

    private SyncJobExecutionResult buildResult(List<SyncDiffPayload> diffs, boolean autoRepair) {
        String summaryJson = """
            {"target":"DEPARTMENT","diffCount":%s,"autoRepair":%s}
            """.formatted(diffs.size(), autoRepair);
        return new SyncJobExecutionResult(SyncRunStatus.SUCCESS, summaryJson, null, diffs);
    }

    private String snapshotDepartment(Department department) {
        return """
            {"deptCode":"%s","deptName":"%s","ldapDn":"%s"}
            """.formatted(safe(department.getDeptCode()), safe(department.getDeptName()), safe(department.getLdapDn()));
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}
