-- 角色变更订阅推送：订阅主表、订阅-角色关联表、事件表增加发布时间列。
-- 历史事件视为已发布，避免上线后向新订阅补推旧数据（首次建账走 /snapshot 接口）。

CREATE TABLE sys_role_push_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订阅ID',
    name VARCHAR(64) NOT NULL COMMENT '订阅名称',
    description VARCHAR(255) NULL COMMENT '订阅说明',
    subject_type VARCHAR(16) NOT NULL COMMENT '订阅主体类型：ROLE_GROUP/GLOBAL',
    subject_id BIGINT NULL COMMENT '主体ID；GLOBAL为空',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    access_token_id BIGINT NOT NULL COMMENT '订阅持有的供给令牌ID',
    mq_queue VARCHAR(128) NOT NULL COMMENT '订阅专属MQ队列名',
    mq_username VARCHAR(64) NOT NULL COMMENT '订阅专属MQ账号',
    mq_password VARCHAR(128) NOT NULL COMMENT 'MQ账号密码，明文存储策略与访问令牌一致',
    creator VARCHAR(64) NOT NULL,
    modifier VARCHAR(64) NOT NULL,
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_push_subscription_queue (mq_queue),
    KEY idx_push_subscription_subject (subject_type, subject_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色变更推送订阅';

CREATE TABLE sys_role_push_subscription_role (
    subscription_id BIGINT NOT NULL COMMENT '订阅ID',
    role_id BIGINT NOT NULL COMMENT '订阅角色ID',
    gmt_create DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (subscription_id, role_id),
    KEY idx_push_subscription_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送订阅-角色关联';

ALTER TABLE sys_role_membership_change
    ADD COLUMN published_at DATETIME(3) NULL COMMENT '推送发布时间；NULL表示待发布' AFTER gmt_create,
    ADD KEY idx_role_membership_published (published_at, id);

UPDATE sys_role_membership_change
SET published_at = gmt_create
WHERE published_at IS NULL;
