package com.company.idm.application.sync.handler;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.SyncJobExecutionResult;
import com.company.idm.application.sync.SyncJobHandler;
import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncJobType;
import com.company.idm.common.enums.SyncRunStatus;
import com.company.idm.common.enums.SyncTargetType;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.AppLdapProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 提供用户维度的 LDAP 对账处理器。
 */
@Component
public class LdapReconcileUserHandler implements SyncJobHandler {

    private final UserRepository userRepository;
    private final LdapDirectoryService ldapDirectoryService;
    private final AppLdapProperties ldapProperties;

    public LdapReconcileUserHandler(
        UserRepository userRepository,
        LdapDirectoryService ldapDirectoryService,
        AppLdapProperties ldapProperties
    ) {
        this.userRepository = userRepository;
        this.ldapDirectoryService = ldapDirectoryService;
        this.ldapProperties = ldapProperties;
    }

    @Override
    public SyncJobType jobType() {
        return SyncJobType.LDAP_RECONCILE_USER;
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
            boolean ldapExists = ldapDirectoryService.existsByUid(user.getUsername());
            if (!ldapExists) {
                diffs.add(new SyncDiffPayload(
                    SyncTargetType.USER,
                    user.getUsername(),
                    SyncDiffType.MISSING_IN_LDAP,
                    snapshotUser(user),
                    null,
                    false
                ));
                continue;
            }
            String expectedDn = buildExpectedDn(user.getUsername());
            if (user.getLdapDn() == null || !user.getLdapDn().equals(expectedDn)) {
                if (autoRepair) {
                    userRepository.save(user.toBuilder().ldapDn(expectedDn).build());
                    continue;
                }
                diffs.add(new SyncDiffPayload(
                    SyncTargetType.USER,
                    user.getUsername(),
                    SyncDiffType.FIELD_MISMATCH,
                    snapshotUser(user),
                    "{\"ldapDn\":\"" + safe(expectedDn) + "\"}",
                    true
                ));
            }
        }
        return diffs;
    }

    private SyncJobExecutionResult buildResult(List<SyncDiffPayload> diffs, boolean autoRepair) {
        String summaryJson = """
            {"target":"USER","diffCount":%s,"autoRepair":%s}
            """.formatted(diffs.size(), autoRepair);
        return new SyncJobExecutionResult(SyncRunStatus.SUCCESS, summaryJson, null, diffs);
    }

    private String buildExpectedDn(String username) {
        return "uid=" + username + "," + ldapProperties.getPeopleOu() + "," + ldapProperties.getBaseDn();
    }

    private String snapshotUser(User user) {
        return """
            {"username":"%s","deptCode":"%s","ldapDn":"%s"}
            """.formatted(safe(user.getUsername()), safe(user.getDeptCode()), safe(user.getLdapDn()));
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}
