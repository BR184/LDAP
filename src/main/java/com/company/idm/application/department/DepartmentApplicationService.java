package com.company.idm.application.department;

import com.company.idm.common.enums.SourceType;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.domain.rbac.PermissionLevelRuleService;
import com.company.idm.domain.user.UserRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartmentApplicationService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final LdapGroupService ldapGroupService;
    private final AuditLogRepository auditLogRepository;
    private final PermissionLevelRuleService permissionLevelRuleService;

    public List<Department> listDepartments() {
        return departmentRepository.findAll().stream()
            .sorted(Comparator.comparing(Department::getAncestorPath).thenComparing(Department::getDeptCode))
            .toList();
    }

    public Department getDepartment(String deptCode) {
        return departmentRepository.findByDeptCode(deptCode)
            .orElseThrow(() -> new BizException("DEPT_NOT_FOUND", "部门不存在"));
    }

    @Transactional
    public Department syncDepartmentToLdap(String deptCode, String operator) {
        permissionLevelRuleService.checkCanManageDepartment(operator);
        Department department = departmentRepository.findByDeptCode(deptCode)
            .orElseThrow(() -> new BizException("DEPT_NOT_FOUND", "部门不存在"));
        String ldapDn = ldapGroupService.existsGroup(deptCode)
            ? ldapGroupService.updateGroup(deptCode, department.getDeptName())
            : ldapGroupService.createGroup(deptCode, department.getDeptName());
        Department synced = department;
        if (!ldapDn.equals(department.getLdapDn())) {
            synced = departmentRepository.save(department.toBuilder().ldapDn(ldapDn).build());
        }
        for (com.company.idm.domain.user.User user : userRepository.findAll()) {
            if (deptCode.equals(user.getDeptCode())) {
                ldapGroupService.addUserToGroup(user.getUserId(), deptCode);
                continue;
            }
            ldapGroupService.removeUserFromGroup(user.getUserId(), deptCode);
        }
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType("DEPT_SYNC_LDAP")
            .bizType("DEPARTMENT")
            .bizId(deptCode)
            .afterJson(synced.getDeptName())
            .result("SUCCESS")
            .build());
        return synced;
    }

    @Transactional
    public Department createDepartment(CreateDepartmentCommand command, String operator) {
        permissionLevelRuleService.checkCanManageDepartment(operator);
        departmentRepository.findByDeptCode(command.deptCode())
            .ifPresent(item -> {
                throw new BizException("DEPT_CODE_DUPLICATE", "部门编码已存在");
            });
        if (command.externalId() != null && !command.externalId().isBlank()) {
            departmentRepository.findByExternalId(command.externalId())
                .ifPresent(item -> {
                    throw new BizException("DEPT_EXTERNAL_ID_DUPLICATE", "外部部门ID已存在");
                });
        }
        Department parent = loadParent(command.parentDeptCode());
        String ancestorPath = buildAncestorPath(parent, command.deptCode());
        int deptLevel = buildDeptLevel(parent);
        Department saved = departmentRepository.save(Department.builder()
            .deptCode(command.deptCode())
            .deptName(command.deptName())
            .parentDeptCode(normalizeParentDeptCode(command.parentDeptCode()))
            .ancestorPath(ancestorPath)
            .deptLevel(deptLevel)
            .sourceType(SourceType.MANUAL)
            .externalId(command.externalId())
            .status(1)
            .build());
        String ldapDn = ldapGroupService.createGroup(saved.getDeptCode(), saved.getDeptName());
        saved = departmentRepository.save(saved.toBuilder().ldapDn(ldapDn).build());
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType("DEPT_CREATE")
            .bizType("DEPARTMENT")
            .bizId(saved.getDeptCode())
            .afterJson(saved.getDeptName())
            .result("SUCCESS")
            .build());
        return saved;
    }

    @Transactional
    public Department updateDepartment(UpdateDepartmentCommand command, String operator) {
        permissionLevelRuleService.checkCanManageDepartment(operator);
        Department existing = departmentRepository.findByDeptCode(command.deptCode())
            .orElseThrow(() -> new BizException("DEPT_NOT_FOUND", "部门不存在"));
        Department parent = loadParent(command.parentDeptCode());
        validateParent(existing, parent);
        String newParentDeptCode = normalizeParentDeptCode(command.parentDeptCode());
        String newAncestorPath = buildAncestorPath(parent, existing.getDeptCode());
        int newDeptLevel = buildDeptLevel(parent);
        Department updated = existing.toBuilder()
            .deptName(command.deptName())
            .parentDeptCode(newParentDeptCode)
            .ancestorPath(newAncestorPath)
            .deptLevel(newDeptLevel)
            .status(command.status())
            .build();
        String ldapDn = ldapGroupService.updateGroup(existing.getDeptCode(), command.deptName());
        updated = departmentRepository.save(updated.toBuilder().ldapDn(ldapDn).build());
        updateDescendantPaths(existing, updated);
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType("DEPT_UPDATE")
            .bizType("DEPARTMENT")
            .bizId(updated.getDeptCode())
            .afterJson(updated.getDeptName())
            .result("SUCCESS")
            .build());
        return updated;
    }

    @Transactional
    public void deleteDepartment(DeleteDepartmentCommand command) {
        permissionLevelRuleService.checkCanManageDepartment(command.operator());
        Department department = departmentRepository.findByDeptCode(command.deptCode())
            .orElseThrow(() -> new BizException("DEPT_NOT_FOUND", "部门不存在"));
        if (departmentRepository.existsChildren(command.deptCode())) {
            throw new BizException("DEPT_DELETE_FORBIDDEN", "当前部门存在子部门，不能直接删除");
        }
        if (userRepository.existsActiveDeptBinding(command.deptCode())) {
            throw new BizException("DEPT_IN_USE", "当前部门仍有关联用户，不能直接删除");
        }
        ldapGroupService.deleteGroup(command.deptCode());
        departmentRepository.deleteByDeptCode(command.deptCode());
        auditLogRepository.save(AuditLog.builder()
            .operator(command.operator())
            .operationType("DEPT_DELETE")
            .bizType("DEPARTMENT")
            .bizId(command.deptCode())
            .afterJson("DELETED")
            .result("SUCCESS")
            .build());
    }

    private Department loadParent(String parentDeptCode) {
        if (parentDeptCode == null || parentDeptCode.isBlank()) {
            return null;
        }
        return departmentRepository.findByDeptCode(parentDeptCode)
            .orElseThrow(() -> new BizException("DEPT_PARENT_NOT_FOUND", "父部门不存在"));
    }

    private void validateParent(Department current, Department parent) {
        if (parent == null) {
            return;
        }
        if (current.getDeptCode().equals(parent.getDeptCode())) {
            throw new BizException("DEPT_PARENT_INVALID", "父部门不能选择自身");
        }
        if (parent.getAncestorPath() != null
            && current.getAncestorPath() != null
            && parent.getAncestorPath().startsWith(current.getAncestorPath() + "/")) {
            throw new BizException("DEPT_PARENT_INVALID", "父部门不能选择当前部门的下级节点");
        }
    }

    private void updateDescendantPaths(Department original, Department updated) {
        List<Department> descendants = departmentRepository.findAll().stream()
            .filter(item -> item.getAncestorPath() != null
                && original.getAncestorPath() != null
                && item.getAncestorPath().startsWith(original.getAncestorPath() + "/"))
            .toList();
        for (Department descendant : descendants) {
            String suffix = descendant.getAncestorPath().substring(original.getAncestorPath().length());
            String newPath = updated.getAncestorPath() + suffix;
            int depth = newPath.isBlank() ? 0 : (int) newPath.chars().filter(ch -> ch == '/').count();
            departmentRepository.save(descendant.toBuilder()
                .ancestorPath(newPath)
                .deptLevel(depth)
                .build());
        }
    }

    private String buildAncestorPath(Department parent, String deptCode) {
        if (parent == null) {
            return "/" + deptCode;
        }
        return parent.getAncestorPath() + "/" + deptCode;
    }

    private int buildDeptLevel(Department parent) {
        return parent == null ? 1 : parent.getDeptLevel() + 1;
    }

    private String normalizeParentDeptCode(String parentDeptCode) {
        return parentDeptCode == null || parentDeptCode.isBlank() ? null : parentDeptCode;
    }
}
