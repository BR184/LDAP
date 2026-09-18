-- 角色组动态订阅与范围提交有序版本（阶段 1）
-- 目标：组订阅范围随组内角色动态变化；同组事件按“提交有序版本”严格排序，
-- 使快照边界与增量恢复不依赖跨库自增 ID 的相邻性。

-- 1) 范围版本头：每个角色组一行；写事件时对该行加锁递增，同组写入被串行化，
--    因此“版本顺序 == 事务提交顺序”，快照可在锁下取到一致边界。
CREATE TABLE IF NOT EXISTS sys_role_scope_version (
    scope_id BIGINT NOT NULL COMMENT '范围ID：角色组ID',
    committed_version BIGINT NOT NULL DEFAULT 0 COMMENT '该范围已提交版本头',
    gmt_modified DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (scope_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='角色供给范围提交有序版本头';

-- 2) 事件表扩展：事件类型、范围版本、结构化载荷与订阅控制信息。
ALTER TABLE sys_role_membership_change
    ADD COLUMN event_type VARCHAR(32) NOT NULL DEFAULT 'MEMBER_CHANGED' COMMENT '事件类型：成员/角色/组/订阅控制' AFTER change_type,
    ADD COLUMN scope_version BIGINT NULL COMMENT '所属范围的提交有序版本；组范围事件必填' AFTER event_type,
    ADD COLUMN payload VARCHAR(1024) NULL COMMENT '结构化变更内容（JSON），承载角色状态、组信息、订阅控制等' AFTER scope_version,
    ADD COLUMN subscription_id BIGINT NULL COMMENT '订阅控制事件的目标订阅ID' AFTER payload,
    ADD COLUMN control_version BIGINT NULL COMMENT '订阅控制版本，独立于组范围版本' AFTER subscription_id;

CREATE INDEX idx_role_change_scope_version ON sys_role_membership_change (role_group_id, scope_version);

-- 2b) 事件表原为“成员变更”专用，成员与角色列均为 NOT NULL；
--     扩展为承载角色目录、角色组与订阅控制事件后，这些列对非成员类事件为空。
ALTER TABLE sys_role_membership_change
    MODIFY COLUMN role_id BIGINT NULL COMMENT '角色ID；组信息类事件可为空',
    MODIFY COLUMN role_code VARCHAR(64) NULL COMMENT '角色编码快照',
    MODIFY COLUMN role_name VARCHAR(64) NULL COMMENT '角色名称快照',
    MODIFY COLUMN role_scope VARCHAR(16) NULL COMMENT '角色作用域快照',
    MODIFY COLUMN user_id BIGINT NULL COMMENT '成员用户主键；非成员类事件为空',
    MODIFY COLUMN member_name VARCHAR(64) NULL COMMENT '成员姓名快照',
    MODIFY COLUMN change_type VARCHAR(16) NULL COMMENT '成员变更类型 ADDED/REMOVED；非成员类事件为空';

-- 3) 订阅表扩展：范围模式（整组动态 / 显式选集）与配置控制版本。
ALTER TABLE sys_role_push_subscription
    ADD COLUMN scope_mode VARCHAR(24) NOT NULL DEFAULT 'SELECTED_ROLES' COMMENT 'DYNAMIC_GROUP=整组动态范围；SELECTED_ROLES=显式角色选集' AFTER subject_id,
    ADD COLUMN config_version BIGINT NOT NULL DEFAULT 1 COMMENT '订阅配置/控制版本：轮换、停用、启用、撤销时递增' AFTER scope_mode;

-- 4) 存量组订阅升级为整组动态范围。
--    经阶段 0 核实：现存组订阅的角色选集恰等于其组内全部角色，升级后范围等价，不构成扩权；
--    全局订阅保留显式选集与原投递语义。
UPDATE sys_role_push_subscription SET scope_mode = 'DYNAMIC_GROUP' WHERE subject_type = 'ROLE_GROUP';

DELETE r FROM sys_role_push_subscription_role r
    INNER JOIN sys_role_push_subscription s ON s.id = r.subscription_id
    WHERE s.subject_type = 'ROLE_GROUP';

-- 5) 存量事件回填：类型按原变更类型映射，组范围事件按组内顺序分配连续版本。
UPDATE sys_role_membership_change
SET event_type = CASE change_type WHEN 'ADDED' THEN 'MEMBER_ADDED' ELSE 'MEMBER_REMOVED' END;

UPDATE sys_role_membership_change c
    INNER JOIN (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY role_group_id ORDER BY id) AS assigned_version
        FROM sys_role_membership_change
        WHERE role_group_id IS NOT NULL
    ) t ON t.id = c.id
SET c.scope_version = t.assigned_version;

INSERT INTO sys_role_scope_version (scope_id, committed_version)
SELECT role_group_id, MAX(scope_version)
FROM sys_role_membership_change
WHERE role_group_id IS NOT NULL AND scope_version IS NOT NULL
GROUP BY role_group_id
ON DUPLICATE KEY UPDATE committed_version = VALUES(committed_version);
