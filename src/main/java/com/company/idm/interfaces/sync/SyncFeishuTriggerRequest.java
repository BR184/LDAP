package com.company.idm.interfaces.sync;

/**
 * 封装飞书同步触发接口的请求参数。
 */
public record SyncFeishuTriggerRequest(String sourceFileName, String sourceFileHash) {
}
