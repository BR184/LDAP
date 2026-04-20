package com.company.idm.test;

import com.company.idm.application.sync.feishu.FeishuDepartmentPayload;
import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.FeishuOpenApiProperties;
import com.company.idm.infrastructure.feishu.FeishuDepartmentRemoteService;
import com.company.idm.infrastructure.feishu.FeishuOpenApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证飞书部门远程拉取服务的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class FeishuDepartmentRemoteServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private FeishuOpenApiClient openApiClient;

    @Test
    void shouldFetchDepartmentsAcrossPagesAndOmitNullPageToken() throws Exception {
        FeishuDepartmentRemoteService service = new FeishuDepartmentRemoteService(openApiClient, buildProperties());
        when(openApiClient.get(eq("/open-apis/contact/v3/departments"), anyMap()))
            .thenReturn(json("""
                {
                  "code": 0,
                  "data": {
                    "page_token": "next-token",
                    "items": [
                      {
                        "open_department_id": "od-1",
                        "department_id": "D001",
                        "name": "Platform",
                        "order": 1
                      },
                      {
                        "open_department_id": "od-2",
                        "department_id": "D002",
                        "name": "Infra",
                        "parent_department_id": "od-1",
                        "order": 2
                      }
                    ]
                  }
                }
                """))
            .thenReturn(json("""
                {
                  "code": 0,
                  "data": {
                    "items": [
                      {
                        "open_department_id": "od-1",
                        "department_id": "D001",
                        "name": "Platform Center",
                        "order": 1
                      }
                    ]
                  }
                }
                """));

        List<FeishuDepartmentPayload> payloads = service.fetchDepartments();

        assertThat(payloads).hasSize(2);
        assertThat(payloads).anySatisfy(payload -> {
            assertThat(payload.externalId()).isEqualTo("od-1");
            assertThat(payload.departmentName()).isEqualTo("Platform Center");
        });
        ArgumentCaptor<Map<String, String>> queryCaptor = ArgumentCaptor.forClass(Map.class);
        verify(openApiClient, times(2)).get(eq("/open-apis/contact/v3/departments"), queryCaptor.capture());
        assertThat(queryCaptor.getAllValues().get(0))
            .containsEntry("department_id_type", "open_department_id")
            .containsEntry("page_size", "100")
            .containsEntry("fetch_child", "true")
            .doesNotContainKey("page_token");
        assertThat(queryCaptor.getAllValues().get(1)).containsEntry("page_token", "next-token");
    }

    @Test
    void shouldRejectWhenRequiredDepartmentFieldMissing() throws Exception {
        FeishuDepartmentRemoteService service = new FeishuDepartmentRemoteService(openApiClient, buildProperties());
        when(openApiClient.get(eq("/open-apis/contact/v3/departments"), anyMap()))
            .thenReturn(json("""
                {
                  "code": 0,
                  "data": {
                    "items": [
                      {
                        "open_department_id": "",
                        "department_id": "",
                        "name": "Platform"
                      }
                    ]
                  }
                }
                """));

        assertThatThrownBy(service::fetchDepartments)
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("FEISHU_DEPARTMENT_FETCH_FAILED");
    }

    private FeishuOpenApiProperties buildProperties() {
        FeishuOpenApiProperties properties = new FeishuOpenApiProperties();
        properties.setPageSize(100);
        return properties;
    }

    private JsonNode json(String content) throws IOException {
        return objectMapper.readTree(content);
    }
}
