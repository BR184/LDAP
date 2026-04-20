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
import com.company.idm.domain.ldap.LdapGroupSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        List<Department> departments = departmentRepository.findAll();
        Set<String> mysqlDeptCodes = new HashSet<>();
        for (Department department : departments) {
            mysqlDeptCodes.add(department.getDeptCode());
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
            LdapGroupSnapshot snapshot = ldapGroupService.findGroupSnapshot(department.getDeptCode());
            if (snapshot == null) {
                continue;
            }
            String expectedDn = ldapGroupService.findGroupDn(department.getDeptCode());
            boolean dnMismatch = !safe(department.getLdapDn()).equals(safe(expectedDn));
            boolean fieldMismatch = !safe(department.getDeptName()).equals(safe(snapshot.getGroupName()));
            if (dnMismatch || fieldMismatch) {
                if (autoRepair) {
                    String repairedDn = ldapGroupService.updateGroup(department.getDeptCode(), department.getDeptName());
                    departmentRepository.save(department.toBuilder().ldapDn(repairedDn).build());
                    continue;
                }
                diffs.add(new SyncDiffPayload(
                    SyncTargetType.DEPARTMENT,
                    department.getDeptCode(),
                    dnMismatch ? SyncDiffType.DN_MISMATCH : SyncDiffType.FIELD_MISMATCH,
                    snapshotDepartment(department),
                    snapshotGroup(snapshot),
                    true
                ));
            }
        }
        for (String ldapGroupCode : ldapGroupService.listAllGroupCodes()) {
            if (!mysqlDeptCodes.contains(ldapGroupCode)) {
                LdapGroupSnapshot snapshot = ldapGroupService.findGroupSnapshot(ldapGroupCode);
                diffs.add(new SyncDiffPayload(
                    SyncTargetType.DEPARTMENT,
                    ldapGroupCode,
                    SyncDiffType.MISSING_IN_MYSQL,
                    null,
                    snapshotGroup(snapshot),
                    false
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

    private String snapshotGroup(LdapGroupSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return """
            {"groupCode":"%s","groupName":"%s","dn":"%s"}
            """.formatted(safe(snapshot.getGroupCode()), safe(snapshot.getGroupName()), safe(snapshot.getDn()));
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}
