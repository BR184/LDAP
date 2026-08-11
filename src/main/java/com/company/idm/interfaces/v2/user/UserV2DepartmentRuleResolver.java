package com.company.idm.interfaces.v2.user;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.user.UserPageQuery;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 解析并归一化 V2 用户筛选的部门规则（department_rules JSON 数组字符串）。
 * 规则结构对齐 lumen_flow 模型日志页：{ mode, deptCode, treeScope, membershipScope, unassigned }。
 * <ul>
 *   <li>mode: include/exclude</li>
 *   <li>treeScope: exact/subtree（subtree 用 ancestor_path 前缀展开含下级）</li>
 *   <li>membershipScope: any/primary/part_time</li>
 *   <li>unassigned: true 表示"未归属部门"特殊规则（忽略 deptCode/treeScope/membershipScope）</li>
 * </ul>
 */
@Component
public class UserV2DepartmentRuleResolver {

    private static final int MAX_RULES = 20;

    private final ObjectMapper objectMapper;

    public UserV2DepartmentRuleResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析 department_rules 并展开为归一化规则。返回空列表表示不限部门。
     */
    public List<UserPageQuery.DepartmentRule> resolve(String departmentRulesJson, List<Department> departments) {
        if (departmentRulesJson == null || departmentRulesJson.isBlank()) {
            return List.of();
        }
        List<RawRule> rawRules;
        try {
            rawRules = objectMapper.readValue(departmentRulesJson, new TypeReference<List<RawRule>>() {
            });
        } catch (Exception exception) {
            throw new BizException("PARAM_INVALID", "部门规则格式错误");
        }
        if (rawRules.size() > MAX_RULES) {
            throw new BizException("PARAM_INVALID", "部门规则不能超过 " + MAX_RULES + " 条");
        }
        Map<String, Department> byCode = indexDepartments(departments);
        List<UserPageQuery.DepartmentRule> rules = new ArrayList<>();
        for (RawRule raw : rawRules) {
            rules.add(toDomainRule(raw, byCode));
        }
        return rules;
    }

    private Map<String, Department> indexDepartments(List<Department> departments) {
        Map<String, Department> byCode = new LinkedHashMap<>();
        if (departments != null) {
            for (Department department : departments) {
                if (department.getDeptCode() != null && !department.getDeptCode().isBlank()) {
                    byCode.putIfAbsent(department.getDeptCode(), department);
                }
            }
        }
        return byCode;
    }

    private UserPageQuery.DepartmentRule toDomainRule(RawRule raw, Map<String, Department> byCode) {
        boolean exclude = "exclude".equalsIgnoreCase(raw.mode());
        if (Boolean.TRUE.equals(raw.unassigned())) {
            return new UserPageQuery.DepartmentRule(exclude, true, List.of(), List.of());
        }
        if (raw.deptCode() == null || raw.deptCode().isBlank()) {
            throw new BizException("PARAM_INVALID", "部门规则缺少 deptCode");
        }
        Set<String> codes = expandSubtree(raw.deptCode().trim(), raw.treeScope(), byCode);
        String membership = raw.membershipScope() == null ? "any" : raw.membershipScope().toLowerCase();
        List<String> primaryCodes = (membership.equals("any") || membership.equals("primary"))
            ? new ArrayList<>(codes) : List.of();
        List<String> partTimeCodes = (membership.equals("any") || membership.equals("part_time"))
            ? new ArrayList<>(codes) : List.of();
        return new UserPageQuery.DepartmentRule(exclude, false, primaryCodes, partTimeCodes);
    }

    /**
     * 展开部门代码集合：exact 仅自身；subtree 含所有 ancestor_path 在该部门路径前缀下的下级部门。
     */
    private Set<String> expandSubtree(String deptCode, String treeScope, Map<String, Department> byCode) {
        Department root = byCode.get(deptCode);
        if (root == null) {
            throw new BizException("PARAM_INVALID", "部门规则引用了不存在的部门: " + deptCode);
        }
        Set<String> codes = new LinkedHashSet<>();
        codes.add(deptCode);
        if (treeScope != null && treeScope.equalsIgnoreCase("subtree")) {
            String rootPath = root.getAncestorPath();
            for (Department department : byCode.values()) {
                String path = department.getAncestorPath();
                if (path != null && (path.equals(rootPath) || path.startsWith(rootPath + "/"))) {
                    codes.add(department.getDeptCode());
                }
            }
        }
        return codes;
    }

    private record RawRule(String mode, String deptCode, String treeScope, String membershipScope, Boolean unassigned) {
    }
}
