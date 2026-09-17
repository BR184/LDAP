-- 角色变更订阅推送与角色供应：变更记录增加“成员平台用户ID（飞书ID）”快照列。
-- 订阅消息、/changes 与 /snapshot 需按平台用户ID（= OpenLDAP uid = 平台登录名）精确匹配成员；
-- 该表本就是事件快照语义（已冗余 member_name/role_code/role_name 等），此处同步冗余飞书ID，
-- 使推送与增量读取零额外查询。存量行按当前用户表回填，用户已删除的行保持为空。

ALTER TABLE sys_role_membership_change
    ADD COLUMN member_user_id VARCHAR(128) NULL COMMENT '成员平台用户ID（飞书ID，变更时刻快照）' AFTER member_name;

UPDATE sys_role_membership_change c
INNER JOIN sys_user u ON c.user_id = u.id
SET c.member_user_id = u.user_id
WHERE c.member_user_id IS NULL;
