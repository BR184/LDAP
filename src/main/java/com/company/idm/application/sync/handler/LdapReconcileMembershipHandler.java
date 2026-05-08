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
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * 鎻愪緵鐢ㄦ埛涓庨儴闂ㄥ垎缁勫叧绯荤淮搴︾殑 LDAP 瀵硅处澶勭悊鍣ㄣ€? */
@Component
public class LdapReconcileMembershipHandler implements SyncJobHandler {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LdapGroupService ldapGroupService;

    public LdapReconcileMembershipHandler(
        UserRepository userRepository,
        DepartmentRepository departmentRepository,
        LdapGroupService ldapGroupService
    ) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.ldapGroupService = ldapGroupService;
    }

    @Override
    public SyncJobType jobType() {
        return SyncJobType.LDAP_RECONCILE_MEMBERSHIP;
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
        for (User user : userRepository.findAll()) {
            Set<String> expectedGroups = new TreeSet<>();
            if (user.getDeptCode() != null && !user.getDeptCode().isBlank()) {
                Department department = departmentRepository.findByDeptCode(user.getDeptCode()).orElse(null);
                if (department != null) {
                    expectedGroups.add(department.getDeptCode());
                    if (autoRepair && !ldapGroupService.existsGroup(department.getDeptCode())) {
                        ldapGroupService.createGroup(department.getDeptCode(), department.getDeptName());
                    }
                }
            }
            Set<String> currentGroups = new TreeSet<>(ldapGroupService.listUserGroups(user.getUserId()));
            if (!currentGroups.equals(expectedGroups)) {
                if (autoRepair) {
                    ldapGroupService.syncUserGroups(user.getUserId(), new ArrayList<>(expectedGroups));
                    continue;
                }
                diffs.add(new SyncDiffPayload(
                    SyncTargetType.MEMBERSHIP,
                    user.getUserId(),
                    SyncDiffType.RELATION_MISMATCH,
                    "{\"expectedGroups\":\"" + safe(String.join(",", expectedGroups)) + "\"}",
                    "{\"currentGroups\":\"" + safe(String.join(",", currentGroups)) + "\"}",
                    true
                ));
            }
        }
        return diffs;
    }

    private SyncJobExecutionResult buildResult(List<SyncDiffPayload> diffs, boolean autoRepair) {
        String summaryJson = """
            {"target":"MEMBERSHIP","diffCount":%s,"autoRepair":%s}
            """.formatted(diffs.size(), autoRepair);
        return new SyncJobExecutionResult(SyncRunStatus.SUCCESS, summaryJson, null, diffs);
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}

