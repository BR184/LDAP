package com.company.idm.application.rolegroup;

import java.util.List;

/**
 * 订阅上下文：接入方凭订阅令牌一次性取得的接入所需信息。
 *
 * <p>这是“令牌即接入”的服务端契约——接入方只需提交令牌，即可取得来源身份、订阅与
 * 角色组身份、协议能力、非敏感状态以及仅供其后端使用的专属连接凭据，无需人工抄录
 * MQ 主机、端口、虚拟主机、队列、账号与密码。秘密字段不缓存、不入日志；订阅停用或
 * 撤销时只返回非敏感状态，不再供应连接凭据与角色目录。
 *
 * @param protocolVersion 协议版本，消费方据此判断能力与兼容性
 * @param sourceId 稳定来源标识（身份中台实例）；切换实例时消费方不得继承检查点与授权
 * @param subscription 订阅身份与非敏感状态
 * @param roleGroup 角色组身份；全局订阅为空
 * @param mq 专属连接凭据；订阅非启用状态时为空
 * @param roles 范围内角色目录（不含成员）；订阅非启用状态时为空
 */
public record RoleSupplyContext(
    int protocolVersion,
    String sourceId,
    SubscriptionInfo subscription,
    RoleGroupInfo roleGroup,
    MqConnection mq,
    List<RoleInfo> roles
) {

    /**
     * 订阅身份信息。
     *
     * @param id 订阅标识
     * @param name 订阅名称（对接方命名）
     * @param status 订阅状态
     * @param scopeMode 范围模式：整组动态或显式选集
     * @param configVersion 配置控制版本，轮换与生命周期变化时递增
     */
    public record SubscriptionInfo(
        Long id,
        String name,
        String status,
        String scopeMode,
        Long configVersion
    ) {
    }

    /**
     * 角色组身份信息。
     *
     * @param id 角色组标识
     * @param name 角色组名称
     */
    public record RoleGroupInfo(Long id, String name) {
    }

    /**
     * 专属连接凭据：仅供接入方后端使用，不得下发到浏览器或写入日志。
     *
     * @param host MQ 主机（对外可达地址）
     * @param port MQ 端口
     * @param vhost 虚拟主机
     * @param queue 专属独占队列
     * @param username 专属消费账号
     * @param password 专属消费密码
     */
    public record MqConnection(
        String host,
        int port,
        String vhost,
        String queue,
        String username,
        String password
    ) {
    }

    /**
     * 范围内角色目录项。
     *
     * @param id 角色稳定标识
     * @param code 角色编码（业务映射契约）
     * @param name 角色名称
     * @param status 角色状态（1 启用 / 0 停用）
     */
    public record RoleInfo(Long id, String code, String name, Integer status) {
    }
}
