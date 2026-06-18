package com.company.idm.application.sync.importplan;

import com.company.idm.common.exception.BizException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImportJsonService {

    private final ObjectMapper objectMapper;

    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BizException("IMPORT_JSON_SERIALIZE_FAILED", "导入对象序列化失败");
        }
    }

    public UserImportSnapshot readUserSnapshot(String json) {
        return read(json, UserImportSnapshot.class);
    }

    public DepartmentImportSnapshot readDepartmentSnapshot(String json) {
        return read(json, DepartmentImportSnapshot.class);
    }

    private <T> T read(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new BizException("IMPORT_JSON_DESERIALIZE_FAILED", "导入对象反序列化失败");
        }
    }
}
