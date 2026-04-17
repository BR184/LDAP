package com.company.idm.interfaces.sync;

/**
 * 封装 LDAP 对账执行接口的请求参数。
 */
public record SyncReconcileRequest(Boolean autoRepair) {
}
