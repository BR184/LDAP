package com.company.idm.infrastructure.feishu;

import com.company.idm.application.sync.feishu.FeishuDepartmentPayload;
import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.FeishuOpenApiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 负责从飞书开放平台拉取部门数据。
 */
@Service
public class FeishuDepartmentRemoteService {

    private final FeishuOpenApiClient openApiClient;
    private final FeishuOpenApiProperties properties;

    public FeishuDepartmentRemoteService(FeishuOpenApiClient openApiClient, FeishuOpenApiProperties properties) {
        this.openApiClient = openApiClient;
        this.properties = properties;
    }

    public List<FeishuDepartmentPayload> fetchDepartments() {
        List<FeishuDepartmentPayload> departments = new ArrayList<>();
        String pageToken = null;
        do {
            Map<String, String> queryParameters = new LinkedHashMap<>();
            queryParameters.put("department_id_type", "open_department_id");
            queryParameters.put("page_size", String.valueOf(properties.getPageSize()));
            queryParameters.put("fetch_child", "true");
            if (pageToken != null && !pageToken.isBlank()) {
                queryParameters.put("page_token", pageToken);
            }
            JsonNode root = openApiClient.get("/open-apis/contact/v3/departments", queryParameters);
            JsonNode items = root.path("data").path("items");
            if (!items.isArray()) {
                throw new BizException("FEISHU_DEPARTMENT_FETCH_FAILED", "飞书部门列表响应格式错误");
            }
            for (JsonNode item : items) {
                String externalId = firstNonBlank(item.path("open_department_id").asText(null), item.path("department_id").asText(null));
                String departmentCode = firstNonBlank(item.path("department_id").asText(null), item.path("open_department_id").asText(null));
                String departmentName = firstNonBlank(item.path("name").asText(null), item.path("i18n_name").path("zh_cn").asText(null));
                String parentExternalId = blankToNull(item.path("parent_department_id").asText(null));
                boolean deleted = item.path("status").path("is_deleted").asBoolean(false);
                if (isBlank(externalId) || isBlank(departmentCode) || isBlank(departmentName)) {
                    throw new BizException("FEISHU_DEPARTMENT_FETCH_FAILED", "飞书部门数据缺少关键字段");
                }
                departments.add(new FeishuDepartmentPayload(
                    externalId,
                    departmentCode,
                    departmentName,
                    parentExternalId,
                    deleted ? 0 : 1,
                    item.path("order").isMissingNode() || item.path("order").isNull() ? null : item.path("order").asInt()
                ));
            }
            pageToken = blankToNull(root.path("data").path("page_token").asText(null));
        } while (pageToken != null && !pageToken.isBlank());
        Map<String, FeishuDepartmentPayload> byExternalId = new LinkedHashMap<>();
        for (FeishuDepartmentPayload payload : departments) {
            byExternalId.put(payload.externalId(), payload);
        }
        return new ArrayList<>(byExternalId.values());
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
