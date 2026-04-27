package com.company.idm.application.sync.feishu;

import java.util.List;

/**
 * 封装一键导入所需的部门与用户标准化数据。
 */
public record FeishuFullImportDocument(
    List<FeishuDepartmentPayload> departments,
    List<FeishuUserPayload> users
) {
}
