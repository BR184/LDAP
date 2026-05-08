package com.company.idm.application.sync.feishu;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncTargetType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapDirectoryService;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 璐熻矗澶勭悊瀵归綈瀵煎叆鏃剁殑缂哄け瀵硅薄娓呯悊銆? */
@Service
public class FeishuImportAlignmentService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LdapDirectoryService ldapDirectoryService;
    private final LdapGroupService ldapGroupService;

    public FeishuImportAlignmentService(
        UserRepository userRepository,
        DepartmentRepository departmentRepository,
        LdapDirectoryService ldapDirectoryService,
        LdapGroupService ldapGroupService
    ) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.ldapDirectoryService = ldapDirectoryService;
        this.ldapGroupService = ldapGroupService;
    }

    public List<SyncDiffPayload> cleanupMissingUsers(Set<String> retainedUserIds) {
        List<SyncDiffPayload> diffs = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            if (!shouldCleanupUser(user, retainedUserIds)) {
                continue;
            }
            cleanupLdapUser(user.getUserId());
            userRepository.save(user.toBuilder()
                .status(UserStatus.DISABLED)
                .employmentStatus(EmploymentStatus.RESIGNED)
                .ldapDn(null)
                .tokenVersion(nextTokenVersion(user))
                .build());
            diffs.add(new SyncDiffPayload(
                SyncTargetType.USER,
                user.getUserId(),
                SyncDiffType.MISSING_IN_SOURCE,
                snapshotUser(user),
                null,
                true
            ));
        }
        return diffs;
    }

    public List<SyncDiffPayload> cleanupMissingDepartments(Set<String> retainedExternalIds) {
        List<SyncDiffPayload> diffs = new ArrayList<>();
        List<Department> departmentsToDelete = departmentRepository.findAll().stream()
            .filter(department -> shouldCleanupDepartment(department, retainedExternalIds))
            .sorted(Comparator.comparingInt((Department department) -> department.getDeptLevel() == null ? 0 : department.getDeptLevel()).reversed())
            .toList();
        for (Department department : departmentsToDelete) {
            if (userRepository.existsActiveDeptBinding(department.getDeptCode())) {
                throw new BizException("FEISHU_ALIGN_DEPARTMENT_IN_USE", "瀵归綈瀵煎叆鏃犳硶鍒犻櫎浠嶆湁鐢ㄦ埛缁戝畾鐨勯儴闂細" + department.getDeptCode());
            }
            ldapGroupService.deleteGroup(department.getDeptCode());
            departmentRepository.deleteByDeptCode(department.getDeptCode());
            diffs.add(new SyncDiffPayload(
                SyncTargetType.DEPARTMENT,
                department.getDeptCode(),
                SyncDiffType.MISSING_IN_SOURCE,
                snapshotDepartment(department),
                null,
                true
            ));
        }
        return diffs;
    }

    private boolean shouldCleanupUser(User user, Set<String> retainedUserIds) {
        return user != null
            && user.getSourceType() == SourceType.FEISHU
            && user.getUserId() != null
            && !retainedUserIds.contains(user.getUserId());
    }

    private boolean shouldCleanupDepartment(Department department, Set<String> retainedExternalIds) {
        return department != null
            && department.getSourceType() == SourceType.FEISHU
            && department.getExternalId() != null
            && !retainedExternalIds.contains(department.getExternalId());
    }

    private int nextTokenVersion(User user) {
        return (user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1;
    }

    private void cleanupLdapUser(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        ldapGroupService.removeUserFromAllGroups(username);
        if (!ldapDirectoryService.existsByUid(username)) {
            return;
        }
        try {
            ldapDirectoryService.deleteUser(username);
        } catch (BizException exception) {
            if (!"LDAP_USER_NOT_FOUND".equals(exception.getCode())) {
                throw exception;
            }
        }
    }

    private String snapshotUser(User user) {
        return """
            {"userId":"%s","deptCode":"%s"}
            """.formatted(safe(user.getUserId()), safe(user.getDeptCode()));
    }

    private String snapshotDepartment(Department department) {
        return """
            {"externalId":"%s","deptCode":"%s","deptName":"%s"}
            """.formatted(safe(department.getExternalId()), safe(department.getDeptCode()), safe(department.getDeptName()));
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}

