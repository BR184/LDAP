package com.company.idm.test;

import com.company.idm.application.sync.feishu.FeishuUserPayload;
import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.FeishuOpenApiProperties;
import com.company.idm.infrastructure.feishu.FeishuOpenApiClient;
import com.company.idm.infrastructure.feishu.FeishuUserRemoteService;
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
 * 验证飞书用户远程拉取服务的单元测试。
 */
@ExtendWith(MockitoExtension.class)
class FeishuUserRemoteServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private FeishuOpenApiClient openApiClient;

    @Test
    void shouldFetchUsersAcrossPagesAndDeriveUsername() throws Exception {
        FeishuUserRemoteService service = new FeishuUserRemoteService(openApiClient, buildProperties());
        when(openApiClient.get(eq("/open-apis/contact/v3/users"), anyMap()))
            .thenReturn(json("""
                {
                  "code": 0,
                  "data": {
                    "page_token": "next-token",
                    "items": [
                      {
                        "open_id": "ou-1",
                        "name": "Alice",
                        "email": "alice@example.com",
                        "department_ids": ["od-1"],
                        "status": {
                          "is_frozen": false,
                          "is_resigned": false,
                          "is_activated": true
                        }
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
                        "open_id": "ou-1",
                        "name": "Alice Updated",
                        "email": "alice@example.com",
                        "department_ids": ["od-1"],
                        "status": {
                          "is_frozen": false,
                          "is_resigned": false,
                          "is_activated": true
                        }
                      }
                    ]
                  }
                }
                """));

        List<FeishuUserPayload> payloads = service.fetchUsers();

        assertThat(payloads).hasSize(1);
        assertThat(payloads.get(0).username()).isEqualTo("alice");
        assertThat(payloads.get(0).realName()).isEqualTo("Alice Updated");
        ArgumentCaptor<Map<String, String>> queryCaptor = ArgumentCaptor.forClass(Map.class);
        verify(openApiClient, times(2)).get(eq("/open-apis/contact/v3/users"), queryCaptor.capture());
        assertThat(queryCaptor.getAllValues().get(0))
            .containsEntry("user_id_type", "open_id")
            .containsEntry("department_id_type", "open_department_id")
            .containsEntry("page_size", "100")
            .doesNotContainKey("page_token");
        assertThat(queryCaptor.getAllValues().get(1)).containsEntry("page_token", "next-token");
    }

    @Test
    void shouldMarkFrozenUsersAsInactive() throws Exception {
        FeishuUserRemoteService service = new FeishuUserRemoteService(openApiClient, buildProperties());
        when(openApiClient.get(eq("/open-apis/contact/v3/users"), anyMap()))
            .thenReturn(json("""
                {
                  "code": 0,
                  "data": {
                    "items": [
                      {
                        "open_id": "ou-2",
                        "name": "Bob",
                        "mobile": "13800138000",
                        "department_ids": ["od-1"],
                        "status": {
                          "is_frozen": true,
                          "is_resigned": false,
                          "is_activated": true
                        }
                      }
                    ]
                  }
                }
                """));

        List<FeishuUserPayload> payloads = service.fetchUsers();

        assertThat(payloads).singleElement().satisfies(payload -> assertThat(payload.status()).isEqualTo(0));
    }

    @Test
    void shouldRejectWhenMainDepartmentMissing() throws Exception {
        FeishuUserRemoteService service = new FeishuUserRemoteService(openApiClient, buildProperties());
        when(openApiClient.get(eq("/open-apis/contact/v3/users"), anyMap()))
            .thenReturn(json("""
                {
                  "code": 0,
                  "data": {
                    "items": [
                      {
                        "open_id": "ou-3",
                        "name": "Chris",
                        "email": "chris@example.com",
                        "department_ids": [],
                        "status": {
                          "is_frozen": false,
                          "is_resigned": false,
                          "is_activated": true
                        }
                      }
                    ]
                  }
                }
                """));

        assertThatThrownBy(service::fetchUsers)
            .isInstanceOf(BizException.class)
            .extracting("code")
            .isEqualTo("FEISHU_USER_FETCH_FAILED");
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
