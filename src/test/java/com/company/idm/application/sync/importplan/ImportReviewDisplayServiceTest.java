package com.company.idm.application.sync.importplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.company.idm.common.enums.SourceType;
import com.company.idm.domain.department.Department;
import com.company.idm.domain.department.DepartmentRepository;
import com.company.idm.domain.sync.ChangeItem;
import com.company.idm.domain.sync.ImportBatch;
import com.company.idm.domain.sync.ImportFieldKey;
import com.company.idm.domain.sync.TargetType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ImportReviewDisplayServiceTest {

    private final DepartmentRepository departmentRepository = Mockito.mock(DepartmentRepository.class);
    private final ImportJsonService jsonService = new ImportJsonService(new ObjectMapper());
    private final ImportReviewDisplayService service = new ImportReviewDisplayService(
        departmentRepository,
        jsonService,
        new ObjectMapper()
    );

    @Test
    void formatsDepartmentChangesAsNamesAndCompletePaths() {
        Department root = department("ROOT", "华云三维", null);
        Department finance = department("FINANCE", "财务部", "/ROOT");
        Department research = department("RESEARCH", "研发部", "/ROOT");
        Department marketing = department("MARKETING", "市场部", "/ROOT");
        when(departmentRepository.findAll()).thenReturn(List.of(root, finance, research));
        ChangeItem plannedDepartment = ChangeItem.builder()
            .targetType(TargetType.DEPARTMENT)
            .afterJson(jsonService.toJson(DepartmentImportSnapshot.from(marketing)))
            .build();
        ImportBatch batch = ImportBatch.builder().changeItems(List.of(plannedDepartment)).build();
        ImportReviewDisplayService.ReviewDirectory directory = service.buildDirectory(batch);

        assertThat(service.department("FINANCE", directory))
            .isEqualTo(new ImportReviewDisplayService.DepartmentPresentation("财务部", "华云三维 / 财务部"));
        assertThat(service.formatValue(ImportFieldKey.USER_PART_TIME_DEPARTMENTS, "[\"RESEARCH\",\"MARKETING\"]", directory))
            .isEqualTo("华云三维 / 研发部\n华云三维 / 市场部");
        assertThat(service.formatValue(ImportFieldKey.USER_MAIN_DEPARTMENT, "MISSING", directory))
            .isEqualTo("部门信息缺失");
    }

    @Test
    void returnsStableFieldLabelAndNeverLeaksUnknownDepartmentCode() {
        when(departmentRepository.findAll()).thenReturn(List.of());
        ImportReviewDisplayService.ReviewDirectory directory = service.buildDirectory(null);
        ChangeItem changeItem = ChangeItem.builder()
            .fieldKey(ImportFieldKey.USER_MAIN_DEPARTMENT)
            .beforeValue("UNKNOWN_DEPT_CODE")
            .afterValue("FINANCE")
            .build();

        ImportReviewDisplayService.FormattedField formatted = service.formatField(changeItem, directory);

        assertThat(formatted.fieldLabel()).isEqualTo("主部门");
        assertThat(formatted.beforeValue()).isEqualTo("部门信息缺失");
        assertThat(formatted.beforeValue()).doesNotContain("UNKNOWN_DEPT_CODE");
    }

    private Department department(String code, String name, String ancestorPath) {
        return Department.builder()
            .deptCode(code)
            .deptName(name)
            .ancestorPath(ancestorPath)
            .sourceType(SourceType.FEISHU)
            .status(1)
            .build();
    }
}
