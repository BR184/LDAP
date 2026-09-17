-- 角色变更订阅推送：订阅开通采用两阶段写入（先建订阅记录，再回填订阅令牌与 MQ 凭证），
-- V54 中以下 4 列在开通完成前尚不存在，放开非空约束以匹配两阶段开通流程；
-- 同时将列注释术语统一为“订阅令牌”。

ALTER TABLE sys_role_push_subscription
    MODIFY COLUMN access_token_id BIGINT NULL COMMENT '订阅持有的订阅令牌ID（开通完成前为空）',
    MODIFY COLUMN mq_queue VARCHAR(128) NULL COMMENT '订阅专属MQ队列名（开通完成前为空）',
    MODIFY COLUMN mq_username VARCHAR(64) NULL COMMENT '订阅专属MQ账号（开通完成前为空）',
    MODIFY COLUMN mq_password VARCHAR(128) NULL COMMENT 'MQ账号密码，明文存储策略与访问令牌一致（开通完成前为空）';
