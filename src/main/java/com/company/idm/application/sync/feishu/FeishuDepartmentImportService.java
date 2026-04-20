package com.company.idm.application.sync.feishu;

import com.company.idm.application.sync.SyncDiffPayload;
import com.company.idm.application.sync.SyncRequestPayload;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.SyncDiffType;
import com.company.idm.common.enums.SyncTargetType;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.ldap.LdapGroupService;
import com.company.idm.infrastructure.feishu.FeishuDepartmentRemoteService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 提供飞书部门导入的差异计算和执行能力。
 */
@Service
public class FeishuDepartmentImportService {

    private final FeishuDepartmentRemoteService departmentRemoteService;
    private final FeishuImportDocumentResolver importDocumentResolver;
    private final DepartmentRepository departmentRepository;
    private final LdapGroupService ldapGroupService;

    public FeishuDepartmentImportService(
        FeishuDepartmentRemoteService departmentRemoteService,
        FeishuImportDocumentResolver importDocumentResolver,
        DepartmentRepository departmentRepository,
        LdapGroupService ldapGroupService
    ) {
        this.departmentRemoteService = departmentRemoteService;
        this.importDocumentResolver = importDocumentResolver;
        this.departmentRepository = departmentRepository;
        this.ldapGroupService = ldapGroupService;
    }

    public FeishuDepartmentImportResult preview(SyncRequestPayload payload) {
        ImportPlan plan = buildPlan(departmentRemoteService.fetchDepartments());
        return new FeishuDepartmentImportResult(plan.newCount, plan.updateCount, plan.noChangeCount, plan.diffs);
    }

    public FeishuDepartmentImportResult execute(SyncRequestPayload payload) {
        ImportPlan plan = buildPlan(departmentRemoteService.fetchDepartments());
        return executePlan(plan);
    }

    public FeishuDepartmentImportResult previewFromDocument(String documentPath) {
        ImportPlan plan = buildPlan(importDocumentResolver.resolveDepartments(documentPath));
        return new FeishuDepartmentImportResult(plan.newCount, plan.updateCount, plan.noChangeCount, plan.diffs);
    }

    public FeishuDepartmentImportResult executeFromDocument(String documentPath) {
        ImportPlan plan = buildPlan(importDocumentResolver.resolveDepartments(documentPath));
        return executePlan(plan);
    }

    private FeishuDepartmentImportResult executePlan(ImportPlan plan) {
        List<PlanItem> executableItems = plan.items.stream()
            .filter(item -> item.changeType() != ChangeType.NO_CHANGE || item.ldapRepairRequired())
            .sorted(Comparator.comparingInt(item -> item.target().getDeptLevel()))
            .toList();
        for (PlanItem item : executableItems) {
            Department saved = departmentRepository.save(item.target());
            String ldapDn = determineLdapDn(item);
            if (ldapDn != null && !ldapDn.equals(saved.getLdapDn())) {
                departmentRepository.save(saved.toBuilder().ldapDn(ldapDn).build());
            }
        }
        return new FeishuDepartmentImportResult(plan.newCount, plan.updateCount, plan.noChangeCount, List.of());
    }

    private String determineLdapDn(PlanItem item) {
        if (!ldapGroupService.existsGroup(item.target().getDeptCode())) {
            return ldapGroupService.createGroup(item.target().getDeptCode(), item.target().getDeptName());
        }
        if (item.changeType() != ChangeType.NO_CHANGE || item.ldapRepairRequired()) {
            return ldapGroupService.updateGroup(item.target().getDeptCode(), item.target().getDeptName());
        }
        return ldapGroupService.findGroupDn(item.target().getDeptCode());
    }

    private ImportPlan buildPlan(List<FeishuDepartmentPayload> departments) {
        validateDuplicates(departments);
        Map<String, Department> existingByExternalId = new LinkedHashMap<>();
        Map<String, Department> existingByDeptCode = new LinkedHashMap<>();
        for (Department department : departmentRepository.findAll()) {
            if (department.getExternalId() != null && !department.getExternalId().isBlank()) {
                existingByExternalId.put(department.getExternalId(), department);
            }
            existingByDeptCode.put(department.getDeptCode(), department);
        }
        Map<String, FeishuDepartmentPayload> payloadByExternalId = new LinkedHashMap<>();
        for (FeishuDepartmentPayload payloadItem : departments) {
            payloadByExternalId.put(payloadItem.externalId(), payloadItem);
        }

        Map<String, Department> resolvedTargets = new LinkedHashMap<>();
        Set<String> visiting = new java.util.HashSet<>();
        List<PlanItem> items = new ArrayList<>();
        int newCount = 0;
        int updateCount = 0;
        int noChangeCount = 0;

        for (FeishuDepartmentPayload payloadItem : departments) {
            Department target = resolveTarget(
                payloadItem,
                payloadByExternalId,
                existingByExternalId,
                existingByDeptCode,
                resolvedTargets,
                visiting
            );
            Department existing = existingByExternalId.get(payloadItem.externalId());
            ChangeType changeType = resolveChangeType(existing, target);
            boolean ldapRepairRequired = existing == null
                || target.getLdapDn() == null
                || !ldapGroupService.existsGroup(target.getDeptCode())
                || !safe(existing == null ? null : existing.getLdapDn()).equals(safe(ldapGroupService.findGroupDn(target.getDeptCode())));
            if (changeType == ChangeType.NEW) {
                newCount++;
            } else if (changeType == ChangeType.NO_CHANGE && !ldapRepairRequired) {
                noChangeCount++;
            } else {
                updateCount++;
            }
            items.add(new PlanItem(existing, target, changeType, ldapRepairRequired));
        }

        List<SyncDiffPayload> allDiffs = new ArrayList<>();
        for (PlanItem item : items) {
            allDiffs.addAll(buildDiffs(item.existing(), item.target(), item.changeType(), item.ldapRepairRequired()));
        }
        return new ImportPlan(items, allDiffs, newCount, updateCount, noChangeCount);
    }

    private Department resolveTarget(
        FeishuDepartmentPayload payload,
        Map<String, FeishuDepartmentPayload> payloadByExternalId,
        Map<String, Department> existingByExternalId,
        Map<String, Department> existingByDeptCode,
        Map<String, Department> resolvedTargets,
        Set<String> visiting
    ) {
        Department cached = resolvedTargets.get(payload.externalId());
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(payload.externalId())) {
            throw new BizException("FEISHU_DEPT_TREE_INVALID", "飞书部门树存在循环引用");
        }
        Department parent = null;
        if (payload.parentExternalId() != null && !payload.parentExternalId().isBlank()) {
            FeishuDepartmentPayload parentPayload = payloadByExternalId.get(payload.parentExternalId());
            if (parentPayload != null) {
                parent = resolveTarget(parentPayload, payloadByExternalId, existingByExternalId, existingByDeptCode, resolvedTargets, visiting);
            } else {
                parent = existingByExternalId.get(payload.parentExternalId());
            }
            if (parent == null) {
                throw new BizException("FEISHU_DEPT_PARENT_NOT_FOUND", "飞书部门父节点不存在");
            }
        }
        Department existingByExternal = existingByExternalId.get(payload.externalId());
        Department existingByCode = existingByDeptCode.get(payload.departmentCode());
        if (existingByExternal != null && !existingByExternal.getDeptCode().equals(payload.departmentCode())) {
            throw new BizException("FEISHU_DEPT_CODE_CONFLICT", "飞书部门 external_id 与 dept_code 映射冲突");
        }
        if (existingByExternal == null && existingByCode != null
            && existingByCode.getExternalId() != null
            && !existingByCode.getExternalId().equals(payload.externalId())) {
            throw new BizException("FEISHU_DEPT_CODE_DUPLICATE", "飞书部门编码已被其他部门占用");
        }
        Department base = existingByExternal != null ? existingByExternal : existingByCode;
        String ancestorPath = parent == null ? "/" + payload.departmentCode() : parent.getAncestorPath() + "/" + payload.departmentCode();
        int deptLevel = parent == null ? 1 : parent.getDeptLevel() + 1;
        Department target = Department.builder()
            .id(base == null ? null : base.getId())
            .deptCode(payload.departmentCode())
            .deptName(payload.departmentName())
            .parentDeptCode(parent == null ? null : parent.getDeptCode())
            .ancestorPath(ancestorPath)
            .deptLevel(deptLevel)
            .sourceType(SourceType.FEISHU)
            .externalId(payload.externalId())
            .ldapDn(base == null ? null : base.getLdapDn())
            .status(normalizeStatus(payload.status()))
            .build();
        resolvedTargets.put(payload.externalId(), target);
        visiting.remove(payload.externalId());
        return target;
    }

    private ChangeType resolveChangeType(Department existing, Department target) {
        if (existing == null) {
            return ChangeType.NEW;
        }
        boolean sameBasic = safe(existing.getDeptName()).equals(safe(target.getDeptName()))
            && safe(existing.getParentDeptCode()).equals(safe(target.getParentDeptCode()))
            && safe(existing.getAncestorPath()).equals(safe(target.getAncestorPath()))
            && intValue(existing.getDeptLevel()) == intValue(target.getDeptLevel())
            && intValue(existing.getStatus()) == intValue(target.getStatus());
        return sameBasic ? ChangeType.NO_CHANGE : ChangeType.UPDATE;
    }

    private List<SyncDiffPayload> buildDiffs(Department existing, Department target, ChangeType changeType, boolean ldapRepairRequired) {
        List<SyncDiffPayload> diffs = new ArrayList<>();
        if (changeType == ChangeType.NEW) {
            diffs.add(new SyncDiffPayload(
                SyncTargetType.DEPARTMENT,
                target.getDeptCode(),
                SyncDiffType.MISSING_IN_MYSQL,
                snapshotTarget(target),
                null,
                true
            ));
            return diffs;
        }
        if (changeType == ChangeType.UPDATE) {
            boolean relationChanged = !safe(existing.getParentDeptCode()).equals(safe(target.getParentDeptCode()))
                || !safe(existing.getAncestorPath()).equals(safe(target.getAncestorPath()))
                || intValue(existing.getDeptLevel()) != intValue(target.getDeptLevel());
            diffs.add(new SyncDiffPayload(
                SyncTargetType.DEPARTMENT,
                target.getDeptCode(),
                relationChanged ? SyncDiffType.RELATION_MISMATCH : SyncDiffType.FIELD_MISMATCH,
                snapshotTarget(target),
                snapshotExisting(existing),
                true
            ));
        } else if (ldapRepairRequired) {
            diffs.add(new SyncDiffPayload(
                SyncTargetType.DEPARTMENT,
                target.getDeptCode(),
                SyncDiffType.FIELD_MISMATCH,
                snapshotTarget(target),
                snapshotExisting(existing),
                true
            ));
        }
        return diffs;
    }

    private void validateDuplicates(List<FeishuDepartmentPayload> departments) {
        Map<String, Integer> externalIdCounter = new HashMap<>();
        Map<String, Integer> deptCodeCounter = new HashMap<>();
        for (FeishuDepartmentPayload payload : departments) {
            externalIdCounter.merge(payload.externalId(), 1, Integer::sum);
            deptCodeCounter.merge(payload.departmentCode(), 1, Integer::sum);
        }
        externalIdCounter.forEach((externalId, count) -> {
            if (count > 1) {
                throw new BizException("FEISHU_DEPT_DUPLICATE_EXTERNAL_ID", "飞书部门 external_id 重复");
            }
        });
        deptCodeCounter.forEach((deptCode, count) -> {
            if (count > 1) {
                throw new BizException("FEISHU_DEPT_DUPLICATE_CODE", "飞书部门编码重复");
            }
        });
    }

    private String snapshotTarget(Department department) {
        return """
            {"externalId":"%s","deptCode":"%s","deptName":"%s","parentDeptCode":"%s","ancestorPath":"%s","deptLevel":%s,"status":%s}
            """.formatted(
            safe(department.getExternalId()),
            safe(department.getDeptCode()),
            safe(department.getDeptName()),
            safe(department.getParentDeptCode()),
            safe(department.getAncestorPath()),
            intValue(department.getDeptLevel()),
            intValue(department.getStatus())
        );
    }

    private String snapshotExisting(Department department) {
        return """
            {"externalId":"%s","deptCode":"%s","deptName":"%s","parentDeptCode":"%s","ancestorPath":"%s","deptLevel":%s,"status":%s,"ldapDn":"%s"}
            """.formatted(
            safe(department.getExternalId()),
            safe(department.getDeptCode()),
            safe(department.getDeptName()),
            safe(department.getParentDeptCode()),
            safe(department.getAncestorPath()),
            intValue(department.getDeptLevel()),
            intValue(department.getStatus()),
            safe(department.getLdapDn())
        );
    }

    private int normalizeStatus(Integer status) {
        return status == null ? 1 : status;
    }

    private int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    private record ImportPlan(
        List<PlanItem> items,
        List<SyncDiffPayload> diffs,
        int newCount,
        int updateCount,
        int noChangeCount
    ) {
    }

    private record PlanItem(
        Department existing,
        Department target,
        ChangeType changeType,
        boolean ldapRepairRequired
    ) {
    }

    private enum ChangeType {
        NEW,
        UPDATE,
        NO_CHANGE
    }
}
