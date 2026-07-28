package com.company.idm.application.department;

import com.company.idm.domain.department.Department;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DepartmentPathService {

    public DepartmentDisplay resolve(String departmentCode, List<Department> departments) {
        if (departmentCode == null || departmentCode.isBlank() || departments == null || departments.isEmpty()) {
            return DepartmentDisplay.empty();
        }
        Map<String, Department> departmentsByCode = new LinkedHashMap<>();
        for (Department department : departments) {
            if (department != null && department.getDeptCode() != null && !department.getDeptCode().isBlank()) {
                departmentsByCode.putIfAbsent(department.getDeptCode(), department);
            }
        }
        Department current = departmentsByCode.get(departmentCode);
        if (current == null) {
            return DepartmentDisplay.empty();
        }

        List<String> pathCodes = splitPath(current.getAncestorPath());
        if (!pathCodes.contains(current.getDeptCode())) {
            pathCodes.add(current.getDeptCode());
        }
        List<String> pathNames = pathCodes.stream()
            .map(departmentsByCode::get)
            .filter(java.util.Objects::nonNull)
            .map(Department::getDeptName)
            .filter(name -> name != null && !name.isBlank())
            .toList();
        String path = pathNames.isEmpty() ? current.getDeptName() : String.join(" / ", pathNames);
        return new DepartmentDisplay(current.getDeptName(), path);
    }

    private List<String> splitPath(String ancestorPath) {
        if (ancestorPath == null || ancestorPath.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(java.util.Arrays.stream(ancestorPath.split("/"))
            .map(String::trim)
            .filter(code -> !code.isBlank())
            .distinct()
            .toList());
    }
}
