package com.company.idm.test;

import com.company.idm.application.sync.feishu.FeishuDepartmentPayload;
import com.company.idm.application.sync.feishu.FeishuFullImportDocument;
import com.company.idm.application.sync.feishu.FeishuImportDocumentResolver;
import com.company.idm.application.user.UsernameGenerationService;
import com.company.idm.common.enums.SourceType;
import com.company.idm.common.enums.UserStatus;
import com.company.idm.common.exception.BizException;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.FeishuFileImportProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeishuImportDocumentResolverTest {

    @TempDir
    Path tempDir;

    @Mock
    private UserRepository userRepository;

    @Test
    void shouldResolveDepartmentDocumentUnderConfiguredRoot() throws Exception {
        Path rootDir = tempDir.resolve("imports");
        Path document = rootDir.resolve("departments/demo.json");
        Files.createDirectories(document.getParent());
        Files.writeString(document, """
            [
              {
                "externalId": "ou_root",
                "departmentCode": "D100",
                "departmentName": "研发中心",
                "parentExternalId": null,
                "status": 1,
                "orderNo": 1
              }
            ]
            """);

        FeishuImportDocumentResolver resolver = buildResolver(rootDir);

        List<FeishuDepartmentPayload> payloads = resolver.resolveDepartments("departments/demo.json");

        assertThat(payloads).singleElement().satisfies(payload -> {
            assertThat(payload.externalId()).isEqualTo("ou_root");
            assertThat(payload.departmentCode()).isEqualTo("D100");
        });
    }

    @Test
    void shouldRejectDocumentOutsideConfiguredRoot() {
        FeishuImportDocumentResolver resolver = buildResolver(tempDir.resolve("imports"));

        assertThatThrownBy(() -> resolver.resolveDepartments("../outside.json"))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("FEISHU_FILE_IMPORT_PATH_INVALID");
    }

    @Test
    void shouldResolveQuotedAbsoluteWorkbookPathUnderConfiguredRoot() throws Exception {
        Path rootDir = tempDir.resolve("imports");
        Path document = rootDir.resolve("bundle/quoted-roster.xlsx");
        Files.createDirectories(document.getParent());
        writeRosterWorkbook(
            document,
            List.<Object[]>of(new Object[] {
                "于善鹏", "+86 13964112234", "3", "在职", "开发部门",
                "用户725015的组织/开发部门", "开发部门", null, null, "276b33cd", null, null
            })
        );
        when(userRepository.findAll()).thenReturn(List.of());

        FeishuImportDocumentResolver resolver = buildResolver(rootDir);

        FeishuFullImportDocument fullImportDocument = resolver.resolveFullImportDocument("\"" + document.toString() + "\"");

        assertThat(fullImportDocument.users()).hasSize(1);
        assertThat(fullImportDocument.users().get(0).username()).isEqualTo("yushanpeng3");
    }

    @Test
    void shouldResolveFullImportBundleDocument() throws Exception {
        Path rootDir = tempDir.resolve("imports");
        Path document = rootDir.resolve("bundle/full-demo.json");
        Files.createDirectories(document.getParent());
        Files.writeString(document, """
            {
              "departments": [
                {
                  "externalId": "ou_root",
                  "departmentCode": "D100",
                  "departmentName": "研发中心",
                  "parentExternalId": null,
                  "status": 1,
                  "orderNo": 1
                }
              ],
              "users": [
                {
                  "externalId": "u001",
                  "username": "zhangsan",
                  "realName": "张三",
                  "email": "zhangsan@corp.local",
                  "mobile": "13900000000",
                  "employeeNo": "E10001",
                  "mainDepartmentExternalId": "ou_root",
                  "status": 1,
                  "orderNo": 1
                }
              ]
            }
            """);

        FeishuImportDocumentResolver resolver = buildResolver(rootDir);

        FeishuFullImportDocument fullImportDocument = resolver.resolveFullImportDocument("bundle/full-demo.json");

        assertThat(fullImportDocument.departments()).hasSize(1);
        assertThat(fullImportDocument.users()).hasSize(1);
        assertThat(fullImportDocument.departments().get(0).departmentCode()).isEqualTo("D100");
        assertThat(fullImportDocument.users().get(0).username()).isEqualTo("zhangsan");
    }

    @Test
    void shouldResolveRosterWorkbookDocumentWithRootDepartment() throws Exception {
        Path rootDir = tempDir.resolve("imports");
        Path document = rootDir.resolve("bundle/roster-demo.xlsx");
        Files.createDirectories(document.getParent());
        writeRosterWorkbook(
            document,
            List.of(
                new Object[] {
                    "于善鹏", "+86 13964112234", "3", "在职", "开发部门",
                    "用户725015的组织/开发部门", "开发部门", null, null, "276b33cd", null, null
                },
                new Object[] {
                    "戴佳伟", "+86 13964113374", "2", "在职", "效能平台",
                    "用户725015的组织/开发部门/平台开发部门/效能平台", "开发部门", "平台开发部门", "效能平台",
                    "78fe8e9b", "daijiawei@corp.local", null
                }
            )
        );
        when(userRepository.findAll()).thenReturn(List.of());

        FeishuImportDocumentResolver resolver = buildResolver(rootDir);

        FeishuFullImportDocument fullImportDocument = resolver.resolveFullImportDocument("bundle/roster-demo.xlsx");

        assertThat(fullImportDocument.departments()).hasSize(4);
        assertThat(fullImportDocument.departments()).extracting(FeishuDepartmentPayload::departmentName)
            .containsExactly("用户725015的组织", "开发部门", "平台开发部门", "效能平台");
        assertThat(fullImportDocument.departments()).allSatisfy(payload -> {
            assertThat(payload.externalId()).startsWith("roster_dept_");
            assertThat(payload.departmentCode()).startsWith("FD_");
        });
        assertThat(fullImportDocument.users()).hasSize(2);
        assertThat(fullImportDocument.users().get(0).externalId()).isEqualTo("276b33cd");
        assertThat(fullImportDocument.users().get(0).username()).isEqualTo("yushanpeng3");
        assertThat(fullImportDocument.users().get(0).mobile()).isEqualTo("13964112234");
        assertThat(fullImportDocument.users().get(1).username()).isEqualTo("daijiawei2");
        assertThat(fullImportDocument.users().get(1).email()).isEqualTo("daijiawei@corp.local");
        assertThat(fullImportDocument.users().get(1).mainDepartmentExternalId())
            .isEqualTo(fullImportDocument.departments().get(3).externalId());
    }

    @Test
    void shouldIgnorePersonalEmailWhenWorkEmailMissing() throws Exception {
        Path rootDir = tempDir.resolve("imports");
        Path document = rootDir.resolve("bundle/roster-work-email-only.xlsx");
        Files.createDirectories(document.getParent());
        writeRosterWorkbook(
            document,
            List.<Object[]>of(new Object[] {
                "于善鹏", "+86 13964112234", "3", "在职", "开发部门",
                "用户725015的组织/开发部门", "开发部门", null, null, "276b33cd", null, "private@example.com"
            })
        );
        when(userRepository.findAll()).thenReturn(List.of());

        FeishuImportDocumentResolver resolver = buildResolver(rootDir);

        FeishuFullImportDocument fullImportDocument = resolver.resolveFullImportDocument("bundle/roster-work-email-only.xlsx");

        assertThat(fullImportDocument.users()).singleElement().satisfies(user ->
            assertThat(user.email()).isNull()
        );
    }

    @Test
    void shouldResolveRealTemplateMobileNumberHeaderAndWorkEmailColumn() throws Exception {
        Path rootDir = tempDir.resolve("imports");
        Path document = rootDir.resolve("bundle/real-template-roster.xlsx");
        Files.createDirectories(document.getParent());
        writeRealTemplateWorkbook(
            document,
            List.<Object[]>of(new Object[] {
                "于善鹏", "+86 13964112234", "3", "在职", "开发部门",
                "用户725015的组织/开发部门", "开发部门", "276b33cd", "1940579892@qq.com"
            })
        );
        when(userRepository.findAll()).thenReturn(List.of());

        FeishuImportDocumentResolver resolver = buildResolver(rootDir);

        FeishuFullImportDocument fullImportDocument = resolver.resolveFullImportDocument("bundle/real-template-roster.xlsx");

        assertThat(fullImportDocument.users()).singleElement().satisfies(user -> {
            assertThat(user.mobile()).isEqualTo("13964112234");
            assertThat(user.email()).isEqualTo("1940579892@qq.com");
        });
    }

    @Test
    void shouldResolveContactMobileHeader() throws Exception {
        Path rootDir = tempDir.resolve("imports");
        Path document = rootDir.resolve("bundle/contact-mobile-roster.xlsx");
        Files.createDirectories(document.getParent());
        writeContactMobileWorkbook(
            document,
            List.<Object[]>of(new Object[] {
                "于善鹏", "+86 13964112234", "3", "正常", "开发部门",
                "用户725015的组织/开发部门", "开发部门", "276b33cd", "1940579892@qq.com"
            })
        );
        when(userRepository.findAll()).thenReturn(List.of());

        FeishuImportDocumentResolver resolver = buildResolver(rootDir);

        FeishuFullImportDocument fullImportDocument = resolver.resolveFullImportDocument("bundle/contact-mobile-roster.xlsx");

        assertThat(fullImportDocument.users()).singleElement().satisfies(user -> {
            assertThat(user.mobile()).isEqualTo("13964112234");
            assertThat(user.status()).isEqualTo(1);
        });
    }

    @Test
    void shouldResolveRosterWhenHeaderStartsFromSecondRow() throws Exception {
        Path rootDir = tempDir.resolve("imports");
        Path document = rootDir.resolve("bundle/second-row-header.xlsx");
        Files.createDirectories(document.getParent());
        writeRosterWorkbookWithBlankFirstRow(
            document,
            List.<Object[]>of(new Object[] {
                "于善鹏", "+86 13964112234", "3", "在职", "开发部门",
                "用户725015的组织/开发部门", "开发部门", null, null, "276b33cd", "1940579892@qq.com", null
            })
        );
        when(userRepository.findAll()).thenReturn(List.of());

        FeishuImportDocumentResolver resolver = buildResolver(rootDir);

        FeishuFullImportDocument fullImportDocument = resolver.resolveFullImportDocument("bundle/second-row-header.xlsx");

        assertThat(fullImportDocument.users()).singleElement().satisfies(user -> {
            assertThat(user.username()).isEqualTo("yushanpeng3");
            assertThat(user.mobile()).isEqualTo("13964112234");
            assertThat(user.email()).isEqualTo("1940579892@qq.com");
        });
    }

    @Test
    void shouldPreferLevelColumnsButKeepRosterRootWhenWorkbookUpdated() throws Exception {
        Path rootDir = tempDir.resolve("imports");
        Path document = rootDir.resolve("bundle/roster-updated.xlsx");
        Files.createDirectories(document.getParent());
        writeRosterWorkbook(
            document,
            List.<Object[]>of(new Object[] {
                "戴佳伟", "+86 13964113374", "2", "在职", "新平台",
                "用户725015的组织/开发部门/平台开发部门/效能平台", "开发部门", "平台开发部门", "新平台",
                "78fe8e9b", null, null
            })
        );
        when(userRepository.findAll()).thenReturn(List.of());

        FeishuImportDocumentResolver resolver = buildResolver(rootDir);

        FeishuFullImportDocument fullImportDocument = resolver.resolveFullImportDocument("bundle/roster-updated.xlsx");

        assertThat(fullImportDocument.departments()).extracting(FeishuDepartmentPayload::departmentName)
            .containsExactly("用户725015的组织", "开发部门", "平台开发部门", "新平台");
        assertThat(fullImportDocument.users()).singleElement().satisfies(user ->
            assertThat(user.mainDepartmentExternalId()).isEqualTo(fullImportDocument.departments().get(3).externalId())
        );
    }

    @Test
    void shouldKeepExistingUsernameAndUseEmployeeNoForNewSameNameUser() throws Exception {
        Path rootDir = tempDir.resolve("imports");
        Path document = rootDir.resolve("bundle/duplicate-name.xlsx");
        Files.createDirectories(document.getParent());
        writeRosterWorkbook(
            document,
            List.of(
                new Object[] {
                    "于善鹏", "+86 13964112234", "3", "在职", "开发部门",
                    "用户725015的组织/开发部门", "开发部门", null, null, "old-user-id", null, null
                },
                new Object[] {
                    "于善鹏", "+86 13964117777", "10023", "在职", "基础开发部门",
                    "用户725015的组织/开发部门/基础开发部门", "开发部门", "基础开发部门", null,
                    "new-user-id", null, null
                }
            )
        );
        when(userRepository.findAll()).thenReturn(List.of(
            User.builder()
                .id(1L)
                .username("yushanpeng")
                .realName("于善鹏")
                .employeeNo("3")
                .status(UserStatus.ENABLED)
                .sourceType(SourceType.FEISHU)
                .externalId("old-user-id")
                .roleCodes(Set.of())
                .build()
        ));

        FeishuImportDocumentResolver resolver = buildResolver(rootDir);

        FeishuFullImportDocument fullImportDocument = resolver.resolveFullImportDocument("bundle/duplicate-name.xlsx");

        assertThat(fullImportDocument.users()).extracting(FeishuUserPayload -> FeishuUserPayload.username())
            .containsExactly("yushanpeng", "yushanpeng10023");
    }

    private void writeRosterWorkbook(Path document, List<Object[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet rosterSheet = workbook.createSheet("在职人员");
            rosterSheet.createRow(0).createCell(0).setCellValue("姓名");
            rosterSheet.getRow(0).createCell(1).setCellValue("手机号");
            rosterSheet.getRow(0).createCell(2).setCellValue("工号");
            rosterSheet.getRow(0).createCell(3).setCellValue("人员状态");
            rosterSheet.getRow(0).createCell(4).setCellValue("部门");
            rosterSheet.getRow(0).createCell(5).setCellValue("部门 (全路径)");
            rosterSheet.getRow(0).createCell(6).setCellValue("一级部门");
            rosterSheet.getRow(0).createCell(7).setCellValue("二级部门");
            rosterSheet.getRow(0).createCell(8).setCellValue("三级部门");
            rosterSheet.getRow(0).createCell(9).setCellValue("用户 ID");
            rosterSheet.getRow(0).createCell(10).setCellValue("工作邮箱");
            rosterSheet.getRow(0).createCell(11).setCellValue("个人邮箱");

            int rowIndex = 1;
            for (Object[] rowData : rows) {
                var row = rosterSheet.createRow(rowIndex++);
                for (int cellIndex = 0; cellIndex < rowData.length; cellIndex++) {
                    Object value = rowData[cellIndex];
                    if (value == null) {
                        continue;
                    }
                    row.createCell(cellIndex).setCellValue(String.valueOf(value));
                }
            }

            try (java.io.OutputStream outputStream = Files.newOutputStream(document)) {
                workbook.write(outputStream);
            }
        }
    }

    private void writeRealTemplateWorkbook(Path document, List<Object[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet rosterSheet = workbook.createSheet("在职人员");
            rosterSheet.createRow(0).createCell(0).setCellValue("姓名");
            rosterSheet.getRow(0).createCell(3).setCellValue("手机号码");
            rosterSheet.getRow(0).createCell(19).setCellValue("工号");
            rosterSheet.getRow(0).createCell(20).setCellValue("人员状态");
            rosterSheet.getRow(0).createCell(21).setCellValue("工作邮箱");
            rosterSheet.getRow(0).createCell(25).setCellValue("部门");
            rosterSheet.getRow(0).createCell(26).setCellValue("部门 (全路径)");
            rosterSheet.getRow(0).createCell(27).setCellValue("一级部门");
            rosterSheet.getRow(0).createCell(45).setCellValue("用户 ID");

            int rowIndex = 1;
            for (Object[] rowData : rows) {
                var row = rosterSheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(String.valueOf(rowData[0]));
                row.createCell(3).setCellValue(String.valueOf(rowData[1]));
                row.createCell(19).setCellValue(String.valueOf(rowData[2]));
                row.createCell(20).setCellValue(String.valueOf(rowData[3]));
                row.createCell(21).setCellValue(String.valueOf(rowData[8]));
                row.createCell(25).setCellValue(String.valueOf(rowData[4]));
                row.createCell(26).setCellValue(String.valueOf(rowData[5]));
                row.createCell(27).setCellValue(String.valueOf(rowData[6]));
                row.createCell(45).setCellValue(String.valueOf(rowData[7]));
            }

            try (java.io.OutputStream outputStream = Files.newOutputStream(document)) {
                workbook.write(outputStream);
            }
        }
    }

    private void writeRosterWorkbookWithBlankFirstRow(Path document, List<Object[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet rosterSheet = workbook.createSheet("在职人员");
            rosterSheet.createRow(0);
            rosterSheet.createRow(1).createCell(0).setCellValue("姓名");
            rosterSheet.getRow(1).createCell(1).setCellValue("手机号");
            rosterSheet.getRow(1).createCell(2).setCellValue("工号");
            rosterSheet.getRow(1).createCell(3).setCellValue("人员状态");
            rosterSheet.getRow(1).createCell(4).setCellValue("部门");
            rosterSheet.getRow(1).createCell(5).setCellValue("部门 (全路径)");
            rosterSheet.getRow(1).createCell(6).setCellValue("一级部门");
            rosterSheet.getRow(1).createCell(7).setCellValue("二级部门");
            rosterSheet.getRow(1).createCell(8).setCellValue("三级部门");
            rosterSheet.getRow(1).createCell(9).setCellValue("用户 ID");
            rosterSheet.getRow(1).createCell(10).setCellValue("工作邮箱");
            rosterSheet.getRow(1).createCell(11).setCellValue("个人邮箱");

            int rowIndex = 2;
            for (Object[] rowData : rows) {
                var row = rosterSheet.createRow(rowIndex++);
                for (int cellIndex = 0; cellIndex < rowData.length; cellIndex++) {
                    Object value = rowData[cellIndex];
                    if (value == null) {
                        continue;
                    }
                    row.createCell(cellIndex).setCellValue(String.valueOf(value));
                }
            }

            try (java.io.OutputStream outputStream = Files.newOutputStream(document)) {
                workbook.write(outputStream);
            }
        }
    }

    private void writeContactMobileWorkbook(Path document, List<Object[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet rosterSheet = workbook.createSheet("在职人员");
            rosterSheet.createRow(0).createCell(0).setCellValue("姓名");
            rosterSheet.getRow(0).createCell(1).setCellValue("联系手机");
            rosterSheet.getRow(0).createCell(2).setCellValue("工号");
            rosterSheet.getRow(0).createCell(3).setCellValue("账号状态");
            rosterSheet.getRow(0).createCell(4).setCellValue("部门");
            rosterSheet.getRow(0).createCell(5).setCellValue("部门 (全路径)");
            rosterSheet.getRow(0).createCell(6).setCellValue("一级部门");
            rosterSheet.getRow(0).createCell(7).setCellValue("用户 ID");
            rosterSheet.getRow(0).createCell(8).setCellValue("工作邮箱");

            int rowIndex = 1;
            for (Object[] rowData : rows) {
                var row = rosterSheet.createRow(rowIndex++);
                for (int cellIndex = 0; cellIndex < rowData.length; cellIndex++) {
                    Object value = rowData[cellIndex];
                    if (value == null) {
                        continue;
                    }
                    row.createCell(cellIndex).setCellValue(String.valueOf(value));
                }
            }

            try (java.io.OutputStream outputStream = Files.newOutputStream(document)) {
                workbook.write(outputStream);
            }
        }
    }

    private FeishuImportDocumentResolver buildResolver(Path rootDir) {
        FeishuFileImportProperties properties = new FeishuFileImportProperties();
        properties.setEnabled(true);
        properties.setRootDir(rootDir.toString());
        properties.setMaxFileSizeBytes(1024 * 1024);
        return new FeishuImportDocumentResolver(
            properties,
            new ObjectMapper(),
            userRepository,
            new UsernameGenerationService()
        );
    }
}
