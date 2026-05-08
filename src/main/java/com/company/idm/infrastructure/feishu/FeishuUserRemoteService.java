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
            Map<String, String> queryParameters = new LinkedHashMap<>();
            queryParameters.put("user_id_type", "open_id");
            queryParameters.put("department_id_type", "open_department_id");
            queryParameters.put("page_size", String.valueOf(properties.getPageSize()));
            if (pageToken != null && !pageToken.isBlank()) {
                queryParameters.put("page_token", pageToken);
            }
            JsonNode root = openApiClient.get("/open-apis/contact/v3/users", queryParameters);
            JsonNode items = root.path("data").path("items");
            if (!items.isArray()) {
                throw new BizException("FEISHU_USER_FETCH_FAILED", "飞书用户列表响应格式错误");
            }
            for (JsonNode item : items) {
                String userId = item.path("open_id").asText(null);
                String realName = item.path("name").asText(null);
                JsonNode departmentIds = item.path("department_ids");
                String mainDepartmentExternalId = departmentIds.isArray() && departmentIds.size() > 0
                    ? departmentIds.get(0).asText(null)
                    : null;
                List<String> partTimeDepartmentExternalIds = new ArrayList<>();
                if (departmentIds.isArray() && departmentIds.size() > 1) {
                    for (int index = 1; index < departmentIds.size(); index++) {
                        String departmentExternalId = blankToNull(departmentIds.get(index).asText(null));
                        if (!isBlank(departmentExternalId)) {
                            partTimeDepartmentExternalIds.add(departmentExternalId);
                        }
                    }
                }
                if (isBlank(userId) || isBlank(realName) || isBlank(mainDepartmentExternalId)) {
                    throw new BizException("FEISHU_USER_FETCH_FAILED", "飞书用户数据缺少关键字段");
                }
                JsonNode statusNode = item.path("status");
                boolean active = !statusNode.path("is_frozen").asBoolean(false)
                    && !statusNode.path("is_resigned").asBoolean(false)
                    && (!statusNode.path("is_activated").isBoolean() || statusNode.path("is_activated").asBoolean(true));
                users.add(new FeishuUserPayload(
                    userId,
                    realName,
                    blankToNull(item.path("email").asText(null)),
                    blankToNull(item.path("mobile").asText(null)),
                    blankToNull(item.path("employee_no").asText(null)),
                    blankToNull(item.path("job_title").asText(null)),
                    blankToNull(item.path("leader").path("name").asText(null)),
                    active ? "正常" : "冻结",
                    mainDepartmentExternalId,
                    List.copyOf(partTimeDepartmentExternalIds),
                    active ? 1 : 0,
                    item.path("orders").isArray() && item.path("orders").size() > 0 ? item.path("orders").get(0).asInt() : null
                ));
            }
            pageToken = blankToNull(root.path("data").path("page_token").asText(null));
        } while (pageToken != null && !pageToken.isBlank());
        Map<String, FeishuUserPayload> byUserId = new LinkedHashMap<>();
        for (FeishuUserPayload payload : users) {
            byUserId.put(payload.userId(), payload);
        }
        return new ArrayList<>(byUserId.values());
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
