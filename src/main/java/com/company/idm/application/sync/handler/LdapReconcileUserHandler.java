package com.company.idm.application.sync.handler;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.SyncJobExecutionResult;
import com.company.idm.application.sync.SyncJobHandler;
import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncJobType;
import com.company.idm.common.enums.SyncRunStatus;
import com.company.idm.common.enums.SyncTargetType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapUserSnapshot;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.AppLdapProperties;
import com.company.idm.infrastructure.ldap.LdapDnHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 鎻愪緵鐢ㄦ埛缁村害鐨?LDAP 瀵硅处澶勭悊鍣ㄣ€? */
@Component
public class LdapReconcileUserHandler implements SyncJobHandler {

    private static final String DEFAULT_RECONCILE_PASSWORD = "123456";

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
        List<User> users = userRepository.findAll();
        Set<String> mysqlUsernames = new HashSet<>();
        for (User user : users) {
            mysqlUsernames.add(user.getUserId());
            boolean ldapExists = ldapDirectoryService.existsByUid(user.getUserId());
            if (!ldapExists) {
                if (autoRepair) {
                    String ldapDn = ldapDirectoryService.createUser(user, DEFAULT_RECONCILE_PASSWORD);
                    if (user.getStatus() == UserStatus.ENABLED) {
                        ldapDirectoryService.enableUser(user.getUserId());
                    } else {
                        ldapDirectoryService.disableUser(user.getUserId());
                    }
                    userRepository.save(user.toBuilder().ldapDn(ldapDn).build());
                    continue;
                }
                diffs.add(new SyncDiffPayload(
                    SyncTargetType.USER,
                    user.getUserId(),
                    SyncDiffType.MISSING_IN_LDAP,
                    snapshotUser(user),
                    null,
                    true
                ));
                continue;
            }
            LdapUserSnapshot snapshot = ldapDirectoryService.findUserSnapshot(user.getUserId());
            if (snapshot == null) {
                continue;
            }
            String expectedDn = buildExpectedDn(user.getUserId());
            boolean dnMismatch = !safe(user.getLdapDn()).equals(safe(expectedDn));
            boolean statusMismatch = !expectedStatus(user.getStatus()).equals(safe(snapshot.getStatus()));
            boolean fieldMismatch = !safe(user.getRealName()).equals(safe(snapshot.getRealName()))
                || !safe(resolveLdapMail(user)).equals(safe(snapshot.getEmail()))
                || !safe(user.getMobile()).equals(safe(snapshot.getMobile()))
                || !safe(user.getEmployeeNo()).equals(safe(snapshot.getEmployeeNo()))
                || !safe(user.getDeptCode()).equals(safe(snapshot.getDeptCode()));
            if (dnMismatch || statusMismatch || fieldMismatch) {
                if (autoRepair) {
                    ldapDirectoryService.updateUser(user);
                    if (user.getStatus() == UserStatus.ENABLED) {
                        ldapDirectoryService.enableUser(user.getUserId());
                    } else {
                        ldapDirectoryService.disableUser(user.getUserId());
                    }
                    userRepository.save(user.toBuilder().ldapDn(expectedDn).build());
                    continue;
                }
                diffs.add(new SyncDiffPayload(
                    SyncTargetType.USER,
                    user.getUserId(),
                    dnMismatch ? SyncDiffType.DN_MISMATCH : (statusMismatch ? SyncDiffType.STATUS_MISMATCH : SyncDiffType.FIELD_MISMATCH),
                    snapshotUser(user),
                    snapshotLdapUser(snapshot),
                    true
                ));
            }
        }
        for (String ldapUsername : ldapDirectoryService.listAllUsernames()) {
            if (!mysqlUsernames.contains(ldapUsername)) {
                diffs.add(new SyncDiffPayload(
                    SyncTargetType.USER,
                    ldapUsername,
                    SyncDiffType.MISSING_IN_MYSQL,
                    null,
                    snapshotLdapUser(ldapDirectoryService.findUserSnapshot(ldapUsername)),
                    false
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
        return LdapDnHelper.buildUserDn(ldapProperties, username);
    }

    private String expectedStatus(UserStatus status) {
        return status == UserStatus.ENABLED ? "ENABLED" : "DISABLED";
    }

    private String snapshotUser(User user) {
        return """
            {"username":"%s","realName":"%s","email":"%s","mobile":"%s","employeeNo":"%s","deptCode":"%s","status":"%s","ldapDn":"%s"}
            """.formatted(
            safe(user.getUserId()),
            safe(user.getRealName()),
            safe(resolveLdapMail(user)),
            safe(user.getMobile()),
            safe(user.getEmployeeNo()),
            safe(user.getDeptCode()),
            expectedStatus(user.getStatus()),
            safe(user.getLdapDn())
        );
    }

    private String snapshotLdapUser(LdapUserSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return """
            {"username":"%s","realName":"%s","email":"%s","mobile":"%s","employeeNo":"%s","deptCode":"%s","status":"%s","ldapDn":"%s"}
            """.formatted(
            safe(snapshot.getUserId()),
            safe(snapshot.getRealName()),
            safe(snapshot.getEmail()),
            safe(snapshot.getMobile()),
            safe(snapshot.getEmployeeNo()),
            safe(snapshot.getDeptCode()),
            safe(snapshot.getStatus()),
            safe(snapshot.getDn())
        );
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    private String resolveLdapMail(User user) {
        if (user == null) {
            return null;
        }
        if (user.getIntranetEmail() != null && !user.getIntranetEmail().isBlank()) {
            return user.getIntranetEmail();
        }
        return user.getEmail();
    }
}

