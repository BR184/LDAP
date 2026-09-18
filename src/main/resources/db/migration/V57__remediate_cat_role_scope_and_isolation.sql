-- 角色组与第三方平台接入角色解绑与隔离治理：
-- 1. 创建专属的“CAT监控平台”角色组，作为接入第三方平台示范与宿主；
-- 2. 将早期误配置为 GLOBAL 的 CAT_ADMIN 角色收敛为该角色组下的专属组角色 (role_scope = 'GROUP')；
-- 3. 彻底解除对其他角色组的全局广播污染，消除历史已删除角色组的脏引用。

-- 创建 CAT 监控平台专属角色组（如果不存在）
INSERT INTO sys_role_group (group_name, remark, status, creator, modifier, gmt_create, gmt_modified)
SELECT 'CAT监控平台', '美团CAT分布式监控平台角色组，面向后续监控系统角色供应与权限集成', 1, 'system', 'system', NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_group WHERE group_name = 'CAT监控平台'
);

-- 为该角色组绑定系统管理员 admin 作为所有者 (OWNER)
INSERT INTO sys_role_group_member (group_id, user_id, member_role, creator, gmt_create, gmt_modified)
SELECT g.id, u.id, 'OWNER', 'system', NOW(), NOW()
FROM sys_role_group g
JOIN sys_user u ON u.user_id = 'admin'
WHERE g.group_name = 'CAT监控平台'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_group_member m WHERE m.group_id = g.id AND m.user_id = u.id
  );

-- 将 CAT_ADMIN 角色作用域收敛为 GROUP，并归属到 CAT 监控平台角色组
UPDATE sys_role r
JOIN sys_role_group g ON g.group_name = 'CAT监控平台'
SET r.role_scope = 'GROUP',
    r.role_group_id = g.id,
    r.modifier = 'system',
    r.gmt_modified = NOW()
WHERE r.role_code = 'CAT_ADMIN';
