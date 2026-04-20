package com.company.idm.interfaces.sync;

import jakarta.validation.constraints.NotBlank;

/**
 * 封装飞书标准化文件导入请求参数。
 */
public record FeishuFileImportRequest(
    @NotBlank(message = "导入文档路径不能为空")
    String documentPath,
    String remark,
    Boolean forceFullSync
) {
}
