package com.company.idm.interfaces.importplan;

import jakarta.validation.constraints.NotBlank;

public record GenerateImportPlanRequest(
    @NotBlank(message = "导入文件路径不能为空")
    String documentPath,
    String remark
) {
}
