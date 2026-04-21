# 项目进展记录 012 - 部门管理设计补充

## 1. 里程碑说明

本次未新增业务代码实现，主要完成部门管理方案的设计对齐与文档补充，确保后续部门 CRUD、飞书部门映射和 LDAP group 联动有统一约束。

完成时间：2026-04-16

## 2. 本次补充内容

### 2.1 部门模块职责补充

已在 `project-design.md` 中明确部门模块当前需要同时承担以下职责：

- 用户资料中的合法部门引用源
- 前端组织树渲染数据源
- LDAP group 同步主数据源
- 业务组织架构树形主数据
- 飞书 `external_id` 与 LDAP `ldap_dn` 的映射承载体

### 2.2 部门模型补充

已补充部门实体的关键字段设计：

- `dept_code`
- `dept_name`
- `parent_dept_code`
- `ancestor_path`
- `dept_level`
- `source_type`
- `external_id`
- `ldap_dn`
- `status`

并明确：

- `dept_code` 为内部稳定业务编码
- `external_id` 为飞书幂等更新定位键
- `ldap_dn` 为 LDAP 节点实际目录位置

### 2.3 部门 CRUD 业务规则补充

本次补充的核心规则包括：

- 部门树支持上下级关系维护
- 父部门必须存在
- 不允许挂载到自身或自身下级节点
- 部门移动时需级联更新 `ancestor_path` 与 `dept_level`
- 删除前必须校验无子部门、无用户引用
- 用户仅允许选择启用中的部门

### 2.4 LDAP 映射规则补充

针对部门同步到 LDAP group，已补充以下规则：

- `dept_code` 映射业务标识属性
- `dept_name` 映射 `description`
- `cn` 建议采用 `${deptCode}_${deptName}`
- `ldap_dn` 回写到 `sys_department`

并新增关键约束：

- 当部门名称变化时，LDAP 节点 DN 也会变化
- 此时应执行 LDAP `rename / modifyDN`
- 重命名成功后回写新的 `ldap_dn`

## 3. 文档修订范围

本次已更新：

- `docs/project-design.md`

主要修订章节包括：

- 模块现状与边界说明
- 部门主数据与组织架构设计
- LDAP 分组同步设计
- `sys_department` 表设计
- 部门接口设计

## 4. 后续建议

下一步建议按本次设计直接推进以下实现：

1. 部门领域模型与仓储扩展
2. 部门 CRUD 应用服务与控制器
3. LDAP group 重命名能力
4. 部门树、外部 ID、LDAP DN 的单元测试与集成测试
