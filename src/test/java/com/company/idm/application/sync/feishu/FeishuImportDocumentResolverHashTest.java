package com.company.idm.application.sync.feishu;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.FeishuFileImportProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class FeishuImportDocumentResolverHashTest {

    @TempDir
    Path rootDir;

    @Test
    void resolvesPersistedImportDocumentByFullSha256() throws Exception {
        String json = """
            {
              "departments": [],
              "users": [{
                "userId": "zhangsan",
                "realName": "张三",
                "employeeNo": "2680",
                "mainDepartmentExternalId": "finance",
                "partTimeDepartmentExternalIds": [],
                "status": 1,
                "orderNo": 1
              }]
            }
            """;
        Files.writeString(rootDir.resolve("roster-upload.json"), json, StandardCharsets.UTF_8);
        FeishuFileImportProperties properties = new FeishuFileImportProperties();
        properties.setRootDir(rootDir.toString());
        FeishuImportDocumentResolver resolver = new FeishuImportDocumentResolver(
            properties,
            new ObjectMapper(),
            Mockito.mock(UserRepository.class)
        );

        FeishuFullImportDocument document = resolver.resolveFullImportDocumentByHash(sha256(json));

        assertThat(document.users()).singleElement()
            .satisfies(user -> {
                assertThat(user.userId()).isEqualTo("zhangsan");
                assertThat(user.employeeNo()).isEqualTo("2680");
            });
    }

    @Test
    void preservesLargeNumericMobileWithoutScientificNotation() throws Exception {
        Path workbookPath = rootDir.resolve("numeric-mobile.xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Sheet1");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("用户 ID");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("联系手机");
            header.createCell(3).setCellValue("部门");
            header.createCell(4).setCellValue("工作邮箱");
            header.createCell(5).setCellValue("工号");
            header.createCell(6).setCellValue("职务");
            header.createCell(7).setCellValue("直属上级");
            header.createCell(8).setCellValue("账号状态");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("zhangsan");
            row.createCell(1).setCellValue("张三");
            row.createCell(2).setCellValue(8619999999999D);
            row.createCell(3).setCellValue("公司/财务部");
            row.createCell(4).setCellValue("zhangsan@crowncad.com");
            row.createCell(5).setCellValue("2680");
            row.createCell(6).setCellValue("资金管理");
            row.createCell(7).setCellValue("刘敏2(+8615662661200)");
            row.createCell(8).setCellValue("正常");
            try (var output = Files.newOutputStream(workbookPath)) {
                workbook.write(output);
            }
        }
        FeishuFileImportProperties properties = new FeishuFileImportProperties();
        properties.setRootDir(rootDir.toString());
        FeishuImportDocumentResolver resolver = new FeishuImportDocumentResolver(
            properties,
            new ObjectMapper(),
            Mockito.mock(UserRepository.class)
        );

        FeishuFullImportDocument document = resolver.resolveFullImportDocumentByHash(sha256(workbookPath));

        assertThat(document.users()).singleElement()
            .extracting(FeishuUserPayload::mobile)
            .isEqualTo("19999999999");
    }

    private String sha256(String content) throws Exception {
        return toHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String sha256(Path path) throws Exception {
        return toHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private String toHex(byte[] digest) {
        StringBuilder result = new StringBuilder();
        for (byte value : digest) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
