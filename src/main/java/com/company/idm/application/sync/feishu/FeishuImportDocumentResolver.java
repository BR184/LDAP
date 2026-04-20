package com.company.idm.application.sync.feishu;

import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.FeishuFileImportProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 负责从受控目录中解析飞书标准化导入文件。
 */
@Component
public class FeishuImportDocumentResolver {

    private final FeishuFileImportProperties properties;
    private final ObjectMapper objectMapper;

    public FeishuImportDocumentResolver(FeishuFileImportProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<FeishuDepartmentPayload> resolveDepartments(String documentPath) {
        return readDocument(documentPath, new TypeReference<List<FeishuDepartmentPayload>>() {
        });
    }

    public List<FeishuUserPayload> resolveUsers(String documentPath) {
        return readDocument(documentPath, new TypeReference<List<FeishuUserPayload>>() {
        });
    }

    private <T> List<T> readDocument(String documentPath, TypeReference<List<T>> typeReference) {
        Path resolvedPath = resolveDocumentPath(documentPath);
        try {
            if (Files.size(resolvedPath) > properties.getMaxFileSizeBytes()) {
                throw new BizException("FEISHU_FILE_IMPORT_TOO_LARGE", "飞书导入文件大小超出限制");
            }
            return objectMapper.readValue(Files.readString(resolvedPath), typeReference);
        } catch (IOException exception) {
            throw new BizException("FEISHU_FILE_IMPORT_READ_FAILED", "飞书导入文件读取失败");
        }
    }

    private Path resolveDocumentPath(String documentPath) {
        if (!properties.isEnabled()) {
            throw new BizException("FEISHU_FILE_IMPORT_DISABLED", "飞书文件导入未启用");
        }
        if (documentPath == null || documentPath.isBlank()) {
            throw new BizException("FEISHU_FILE_IMPORT_PATH_INVALID", "飞书导入文件路径不能为空");
        }
        Path rootPath = Paths.get(properties.getRootDir()).toAbsolutePath().normalize();
        Path candidatePath = Paths.get(documentPath);
        Path resolvedPath = candidatePath.isAbsolute()
            ? candidatePath.toAbsolutePath().normalize()
            : rootPath.resolve(candidatePath).normalize();
        if (!resolvedPath.startsWith(rootPath)) {
            throw new BizException("FEISHU_FILE_IMPORT_PATH_INVALID", "飞书导入文件路径不在允许目录内");
        }
        if (!resolvedPath.getFileName().toString().toLowerCase().endsWith(".json")) {
            throw new BizException("FEISHU_FILE_IMPORT_FORMAT_INVALID", "飞书导入文件仅支持 JSON 格式");
        }
        if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
            throw new BizException("FEISHU_FILE_IMPORT_NOT_FOUND", "飞书导入文件不存在");
        }
        return resolvedPath;
    }
}
