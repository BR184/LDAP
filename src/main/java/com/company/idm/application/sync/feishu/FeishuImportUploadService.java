package com.company.idm.application.sync.feishu;

import com.company.idm.common.exception.BizException;
import com.company.idm.infrastructure.config.FeishuFileImportProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 负责将上传文件保存到受控导入目录。
 */
@Service
public class FeishuImportUploadService {

    private final FeishuFileImportProperties properties;

    public FeishuImportUploadService(FeishuFileImportProperties properties) {
        this.properties = properties;
    }

    public String store(MultipartFile file) {
        if (!properties.isEnabled()) {
            throw new BizException("FEISHU_FILE_IMPORT_DISABLED", "飞书文件导入未启用");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException("FEISHU_FILE_IMPORT_NOT_FOUND", "上传文件不能为空");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BizException("FEISHU_FILE_IMPORT_TOO_LARGE", "飞书导入文件大小超出限制");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BizException("FEISHU_FILE_IMPORT_FORMAT_INVALID", "上传文件名不能为空");
        }
        String normalizedFilename = Paths.get(originalFilename).getFileName().toString().trim();
        String lowerCaseName = normalizedFilename.toLowerCase(Locale.ROOT);
        if (!lowerCaseName.endsWith(".xlsx") && !lowerCaseName.endsWith(".json")) {
            throw new BizException("FEISHU_FILE_IMPORT_FORMAT_INVALID", "飞书导入文件仅支持 JSON 或 XLSX 格式");
        }

        Path rootDir = Paths.get(properties.getRootDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDir);
            String storedFilename = buildStoredFilename(normalizedFilename);
            Path targetPath = rootDir.resolve(storedFilename).normalize();
            if (!targetPath.startsWith(rootDir)) {
                throw new BizException("FEISHU_FILE_IMPORT_PATH_INVALID", "上传文件路径不在允许目录内");
            }
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return targetPath.toString();
        } catch (IOException exception) {
            throw new BizException("FEISHU_FILE_IMPORT_READ_FAILED", "飞书导入文件读取失败");
        }
    }

    private String buildStoredFilename(String originalFilename) {
        int dotIndex = originalFilename.lastIndexOf('.');
        String baseName = dotIndex > 0 ? originalFilename.substring(0, dotIndex) : originalFilename;
        String extension = dotIndex > 0 ? originalFilename.substring(dotIndex) : "";
        return baseName + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + extension;
    }
}
