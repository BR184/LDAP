package com.company.idm.infrastructure.feishu;

/**
 * 定义飞书租户访问令牌服务接口。
 */
public interface FeishuAccessTokenService {

    String getTenantAccessToken();
}
