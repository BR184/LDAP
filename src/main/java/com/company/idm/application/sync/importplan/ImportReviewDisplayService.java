package com.company.idm.application.sync.importplan;

import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ImportBatch;
import com.company.idm.domain.sync.ImportFieldKey;
import com.company.idm.domain.sync.TargetType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Builds the human-readable import review projection from database departments and batch snapshots.
 */
@Service
@RequiredArgsConstructor
public class ImportReviewDisplayService {

    private static final String UNKNOWN_DEPARTMENT = "部门信息缺失";
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final DepartmentRepository departmentRepository;
    private final ImportJsonService jsonService;
    private final ObjectMapper objectMapper;

    public ReviewDirectory buildDirectory(ImportBatch batch) {
        Map<String, DepartmentNode> nodes = new LinkedHashMap<>();
        for (Department department : departmentRepository.findAll()) {
            add(nodes, DepartmentNode.from(department));
        }
        if (batch != null && batch.getChangeItems() != null) {
            for (ChangeItem item : batch.getChangeItems()) {
                if (item.getTargetType() != TargetType.DEPARTMENT) {
                    continue;
                }
                add(nodes, DepartmentNode.from(snapshot(item.getBeforeJson())));
                add(nodes, DepartmentNode.from(snapshot(item.getAfterJson())));
            }
        }
        return new ReviewDirectory(Map.copyOf(nodes));
    }

    public DepartmentPresentation department(String deptCode, ReviewDirectory directory) {
        if (deptCode == null || deptCode.isBlank()) {
            return DepartmentPresentation.empty();
        }
        DepartmentNode department = directory.nodes().get(deptCode.trim());
        if (department == null || isBlank(department.name())) {
            return new DepartmentPresentation(UNKNOWN_DEPARTMENT, UNKNOWN_DEPARTMENT);
        }
        String path = pathFromCodes(pathCodes(department.ancestorPath(), department.code()), directory);
        return new DepartmentPresentation(department.name(), path);
    }

    public FormattedField formatField(ChangeItem item, ReviewDirectory directory) {
        ImportFieldKey fieldKey = item.getFieldKey();
        return new FormattedField(
            fieldKey,
            fieldKey == null ? null : fieldKey.getLabel(),
            formatValue(fieldKey, item.getBeforeValue(), directory),
            formatValue(fieldKey, item.getAfterValue(), directory)
        );
    }

    public String formatValue(ImportFieldKey fieldKey, String value, ReviewDirectory directory) {
        if (value == null || value.isBlank()) {
            return "--";
        }
        if (fieldKey == null) {
            return value;
        }
        return switch (fieldKey) {
            case USER_MAIN_DEPARTMENT, DEPARTMENT_PARENT -> department(value, directory).departmentPath();
            case USER_PART_TIME_DEPARTMENTS -> formatDepartments(value, directory);
            case DEPARTMENT_PATH -> pathFromCodes(pathCodes(value, null), directory);
            case USER_EMPLOYMENT_STATUS -> employmentStatus(value);
            case DEPARTMENT_STATUS -> departmentStatus(value);
            default -> value;
        };
    }

    private DepartmentImportSnapshot snapshot(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return jsonService.readDepartmentSnapshot(json);
    }

    private void add(Map<String, DepartmentNode> nodes, DepartmentNode node) {
        if (node != null && !isBlank(node.code())) {
            nodes.put(node.code(), node);
        }
    }

    private String formatDepartments(String rawValue, ReviewDirectory directory) {
        List<String> codes;
        try {
            codes = objectMapper.readValue(rawValue, STRING_LIST_TYPE);
        } catch (Exception exception) {
            return UNKNOWN_DEPARTMENT;
        }
        if (codes == null || codes.isEmpty()) {
            return "--";
        }
        return codes.stream()
            .map(code -> department(code, directory).departmentPath())
            .distinct()
            .collect(java.util.stream.Collectors.joining("\n"));
    }

    private String pathFromCodes(List<String> codes, ReviewDirectory directory) {
        List<String> names = new ArrayList<>();
        for (String code : codes) {
            DepartmentNode node = directory.nodes().get(code);
            if (node != null && !isBlank(node.name())) {
                names.add(node.name());
            }
        }
        return names.isEmpty() ? UNKNOWN_DEPARTMENT : String.join(" / ", names);
    }

    private List<String> pathCodes(String ancestorPath, String fallbackCode) {
        List<String> result = new ArrayList<>();
        if (!isBlank(ancestorPath)) {
            for (String code : ancestorPath.split("/")) {
                String normalized = code.trim();
                if (!normalized.isBlank() && !result.contains(normalized)) {
                    result.add(normalized);
                }
            }
        }
        if (!isBlank(fallbackCode) && !result.contains(fallbackCode)) {
            result.add(fallbackCode);
        }
        return result;
    }

    private String employmentStatus(String value) {
        return "ACTIVE".equalsIgnoreCase(value) ? "在职" : "RESIGNED".equalsIgnoreCase(value) ? "离职" : value;
    }

    private String departmentStatus(String value) {
        return "1".equals(value) ? "启用" : "0".equals(value) ? "停用" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ReviewDirectory(Map<String, DepartmentNode> nodes) {
    }

    public record DepartmentPresentation(String departmentName, String departmentPath) {
        private static DepartmentPresentation empty() {
            return new DepartmentPresentation(null, null);
        }
    }

    public record FormattedField(ImportFieldKey fieldKey, String fieldLabel, String beforeValue, String afterValue) {
    }

    public record DepartmentNode(String code, String name, String ancestorPath) {
        private static DepartmentNode from(Department department) {
            return department == null ? null : new DepartmentNode(
                department.getDeptCode(),
                department.getDeptName(),
                department.getAncestorPath()
            );
        }

        private static DepartmentNode from(DepartmentImportSnapshot snapshot) {
            return snapshot == null ? null : new DepartmentNode(
                snapshot.deptCode(),
                snapshot.deptName(),
                snapshot.ancestorPath()
            );
        }
    }
}
