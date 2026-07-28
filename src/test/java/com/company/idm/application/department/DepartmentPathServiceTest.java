package com.company.idm.application.department;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.idm.domain.department.Department;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepartmentPathServiceTest {

    private final DepartmentPathService service = new DepartmentPathService();

    @Test
    void resolvesCompleteDepartmentPathFromAncestorCodes() {
        List<Department> departments = List.of(
            department(1L, "ROOT", "华云三维", "ROOT"),
            department(2L, "RND", "研发中心", "ROOT/RND"),
            department(3L, "PLATFORM", "平台研发部", "ROOT/RND/PLATFORM")
        );

        DepartmentDisplay display = service.resolve("PLATFORM", departments);

        assertThat(display.departmentName()).isEqualTo("平台研发部");
        assertThat(display.departmentPath()).isEqualTo("华云三维 / 研发中心 / 平台研发部");
    }

    @Test
    void returnsEmptyDisplayWhenDepartmentDoesNotExist() {
        DepartmentDisplay display = service.resolve("UNKNOWN", List.of());

        assertThat(display.departmentName()).isNull();
        assertThat(display.departmentPath()).isNull();
    }

    private Department department(Long id, String code, String name, String ancestorPath) {
        return Department.builder()
            .id(id)
            .deptCode(code)
            .deptName(name)
            .ancestorPath(ancestorPath)
            .status(1)
            .build();
    }
}
