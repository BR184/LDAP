package com.company.idm.test;

import com.company.idm.application.sync.feishu.FeishuDepartmentPayload;
import com.company.idm.application.sync.feishu.FeishuImportDocumentResolver;
import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.FeishuFileImportProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证飞书标准化文件解析器的单元测试。
 */
class FeishuImportDocumentResolverTest {

    @TempDir
    Path tempDir;

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

        FeishuImportDocumentResolver resolver = new FeishuImportDocumentResolver(buildProperties(rootDir), new ObjectMapper());

        List<FeishuDepartmentPayload> payloads = resolver.resolveDepartments("departments/demo.json");

        assertThat(payloads).singleElement().satisfies(payload -> {
            assertThat(payload.externalId()).isEqualTo("ou_root");
            assertThat(payload.departmentCode()).isEqualTo("D100");
        });
    }

    @Test
    void shouldRejectDocumentOutsideConfiguredRoot() {
        FeishuImportDocumentResolver resolver = new FeishuImportDocumentResolver(buildProperties(tempDir.resolve("imports")), new ObjectMapper());

        assertThatThrownBy(() -> resolver.resolveDepartments("../outside.json"))
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("FEISHU_FILE_IMPORT_PATH_INVALID");
    }

    private FeishuFileImportProperties buildProperties(Path rootDir) {
        FeishuFileImportProperties properties = new FeishuFileImportProperties();
        properties.setEnabled(true);
        properties.setRootDir(rootDir.toString());
        properties.setMaxFileSizeBytes(1024 * 1024);
        return properties;
    }
}
