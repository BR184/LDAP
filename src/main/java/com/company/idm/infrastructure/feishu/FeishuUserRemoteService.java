package com.company.idm.infrastructure.feishu;

import com.company.idm.application.sync.feishu.FeishuUserPayload;
import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.FeishuOpenApiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 负责从飞书开放平台拉取用户数据。
 */
@Service
public class FeishuUserRemoteService {

    private final FeishuOpenApiClient openApiClient;
    private final FeishuOpenApiProperties properties;

    public FeishuUserRemoteService(FeishuOpenApiClient openApiClient, FeishuOpenApiProperties properties) {
        this.openApiClient = openApiClient;
        this.properties = properties;
    }

    public List<FeishuUserPayload> fetchUsers() {
        List<FeishuUserPayload> users = new ArrayList<>();
        String pageToken = null;
        do {
            JsonNode root = openApiClient.get("/open-apis/contact/v3/users", Map.of(
                "user_id_type", "open_id",
                "department_id_type", "open_department_id",
                "page_size", String.valueOf(properties.getPageSize()),
                "page_token", blankToNull(pageToken)
            ));
            JsonNode items = root.path("data").path("items");
            if (!items.isArray()) {
                throw new BizException("FEISHU_USER_FETCH_FAILED", "飞书用户列表响应格式错误");
            }
            for (JsonNode item : items) {
                String externalId = item.path("open_id").asText(null);
                String username = deriveUsername(item);
                String realName = item.path("name").asText(null);
                JsonNode departmentIds = item.path("department_ids");
                String mainDepartmentExternalId = departmentIds.isArray() && departmentIds.size() > 0
                    ? departmentIds.get(0).asText(null)
                    : null;
                if (isBlank(externalId) || isBlank(username) || isBlank(realName) || isBlank(mainDepartmentExternalId)) {
                    throw new BizException("FEISHU_USER_FETCH_FAILED", "飞书用户数据缺少关键字段");
                }
                boolean active = !item.path("status").path("is_frozen").asBoolean(false)
                    && !item.path("status").path("is_resigned").asBoolean(false)
                    && !item.path("status").path("is_activated").isBoolean() || item.path("status").path("is_activated").asBoolean(true);
                users.add(new FeishuUserPayload(
                    externalId,
                    username,
                    realName,
                    blankToNull(item.path("email").asText(null)),
                    blankToNull(item.path("mobile").asText(null)),
                    blankToNull(item.path("employee_no").asText(null)),
                    mainDepartmentExternalId,
                    active ? 1 : 0,
                    item.path("orders").isArray() && item.path("orders").size() > 0 ? item.path("orders").get(0).asInt() : null
                ));
            }
            pageToken = blankToNull(root.path("data").path("page_token").asText(null));
        } while (pageToken != null && !pageToken.isBlank());
        Map<String, FeishuUserPayload> byExternalId = new LinkedHashMap<>();
        for (FeishuUserPayload payload : users) {
            byExternalId.put(payload.externalId(), payload);
        }
        return new ArrayList<>(byExternalId.values());
    }

    private String deriveUsername(JsonNode item) {
        String employeeNo = blankToNull(item.path("employee_no").asText(null));
        if (!isBlank(employeeNo)) {
            return employeeNo;
        }
        String email = blankToNull(item.path("email").asText(null));
        if (!isBlank(email) && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        String mobile = blankToNull(item.path("mobile").asText(null));
        if (!isBlank(mobile)) {
            return mobile;
        }
        return blankToNull(item.path("open_id").asText(null));
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
