package com.company.idm.application.sync.feishu;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncTargetType;
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
 * 负责处理对齐导入时的缺失对象清理。
 */
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

    public List<SyncDiffPayload> cleanupMissingUsers(Set<String> retainedExternalIds) {
        List<SyncDiffPayload> diffs = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            if (!shouldCleanupUser(user, retainedExternalIds)) {
                continue;
            }
            ldapGroupService.removeUserFromAllGroups(user.getUsername());
            ldapDirectoryService.deleteUser(user.getUsername());
            userRepository.removeAllRoles(user.getId());
            userRepository.logicalDelete(user.getId(), buildRecycledUsername(user), nextTokenVersion(user));
            diffs.add(new SyncDiffPayload(
                SyncTargetType.USER,
                user.getUsername(),
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
            if (userRepository.existsDeptBinding(department.getDeptCode())) {
                throw new BizException("FEISHU_ALIGN_DEPARTMENT_IN_USE", "对齐导入无法删除仍有用户绑定的部门：" + department.getDeptCode());
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

    private boolean shouldCleanupUser(User user, Set<String> retainedExternalIds) {
        return user != null
            && user.getSourceType() == SourceType.FEISHU
            && user.getExternalId() != null
            && !retainedExternalIds.contains(user.getExternalId());
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

    private String buildRecycledUsername(User user) {
        return user.getUsername() + "__deleted__" + user.getId();
    }

    private String snapshotUser(User user) {
        return """
            {"externalId":"%s","username":"%s","deptCode":"%s"}
            """.formatted(safe(user.getExternalId()), safe(user.getUsername()), safe(user.getDeptCode()));
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
