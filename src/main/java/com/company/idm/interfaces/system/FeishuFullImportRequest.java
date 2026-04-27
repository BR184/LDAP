package com.company.idm.interfaces.system;

import com.company.idm.common.enums.ImportMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 封装飞书一键导入请求。
 */
public record FeishuFullImportRequest(
    @NotBlank(message = "导入文件路径不能为空")
    String documentPath,
    String remark,
    @NotNull(message = "导入模式不能为空")
    ImportMode importMode
) {
}
