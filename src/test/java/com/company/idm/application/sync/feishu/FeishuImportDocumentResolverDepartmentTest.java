package com.company.idm.application.sync.feishu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.FeishuFileImportProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

class FeishuImportDocumentResolverDepartmentTest {

    @TempDir
    Path rootDir;

    @ParameterizedTest
    @MethodSource("departmentSeparators")
    void resolvesAllDepartmentPathsInSourceOrderAndRemovesDuplicates(String separator) throws Exception {
        Path workbookPath = rootDir.resolve("multiple-departments.xlsx");
        String finance = "华云三维/财务部";
        String research = "华云三维/研发部";
        String marketing = "华云三维/市场部";
        writeWorkbook(workbookPath, String.join(separator, finance, research, marketing, research));
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        when(userRepository.findAll()).thenReturn(List.of());
        FeishuFileImportProperties properties = new FeishuFileImportProperties();
        properties.setRootDir(rootDir.toString());
        FeishuImportDocumentResolver resolver = new FeishuImportDocumentResolver(
            properties,
            new ObjectMapper(),
            userRepository
        );

        FeishuFullImportDocument document = resolver.resolveFullImportDocument(workbookPath.toString());
        FeishuUserPayload user = document.users().get(0);
        Map<String, String> departmentNameByExternalId = document.departments().stream()
            .collect(java.util.stream.Collectors.toMap(
                FeishuDepartmentPayload::externalId,
                FeishuDepartmentPayload::departmentName
            ));

        assertThat(departmentNameByExternalId.get(user.mainDepartmentExternalId())).isEqualTo("财务部");
        assertThat(user.partTimeDepartmentExternalIds())
            .extracting(departmentNameByExternalId::get)
            .containsExactly("研发部", "市场部");
    }

    private static Stream<Arguments> departmentSeparators() {
        return Stream.of(
            Arguments.of(","),
            Arguments.of("，"),
            Arguments.of(";"),
            Arguments.of("；"),
            Arguments.of("\n")
        );
    }

    private void writeWorkbook(Path workbookPath, String departments) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("花名册");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("用户 ID");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("工号");
            header.createCell(3).setCellValue("部门");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("zhangsan");
            row.createCell(1).setCellValue("张三");
            row.createCell(2).setCellValue("2680");
            row.createCell(3).setCellValue(departments);
            try (var output = Files.newOutputStream(workbookPath)) {
                workbook.write(output);
            }
        }
    }
}
