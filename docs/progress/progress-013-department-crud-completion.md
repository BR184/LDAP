# 项目进展记录 013 - 部门 CRUD 收口完成

## 1. 里程碑说明

本次完成了部门管理模块从“设计补充”到“代码、测试、文档全部收口”的落地，实现了部门树、部门 CRUD、LDAP group 映射和名称变更触发 LDAP 节点重命名的完整闭环。

完成时间：2026-04-16

## 2. 本次完成内容

### 2.1 部门领域与应用服务落地

已新增并补全以下能力：

- `DepartmentApplicationService`
- 部门创建命令、更新命令、删除命令
- 部门详情、部门树、部门创建、部门更新、部门删除

本次实现的核心规则包括：

- `dept_code` 全局唯一且创建后不修改
- 支持树形组织架构
- 支持父节点校验与防循环校验
- 部门移动时级联更新 `ancestor_path` 与 `dept_level`
- 删除前校验无子部门、无用户引用

### 2.2 LDAP group 联动补全

围绕部门与 LDAP group 的固定映射，本次已完成：

- 创建部门时自动创建 LDAP group
- 更新部门名称时执行 LDAP group 重命名
- 更新后回写新的 `ldap_dn`
- 删除部门时删除 LDAP group
- 用户流程首次补建部门 group 时补偿回写 `sys_department.ldap_dn`

当前 LDAP group 命名规则为：

- `cn=${deptCode}_${deptName}`

### 2.3 数据模型与迁移补全

新增部门扩展字段：

- `ancestor_path`
- `dept_level`
- `ldap_dn`

并补充以下约束与权限点：

- `uk_sys_department_external_id`
- `uk_sys_department_ldap_dn`
- `DEPT_TREE`
- `DEPT_DETAIL`
- `DEPT_CREATE`
- `DEPT_UPDATE`
- `DEPT_DELETE`

## 3. 测试补充

本次新增和补强的测试包括：

- 部门应用服务单元测试
- 部门 CRUD 集成测试
- LDAP group DN 重命名单元测试

重点验证场景：

- 部门创建
- 部门更新并移动节点
- 子孙路径级联更新
- 名称变更触发 LDAP DN 变化
- 删除前引用校验
- 完整接口链路可运行

## 4. 文档更新

已同步更新：

- `docs/project-design.md`

本次补充的文档点包括：

- 部门模块当前状态从“设计补充”调整为“一期实现简版”
- 部门当前原型落地说明
- LDAP 分组同步当前实现状态
- `sys_department` 索引与审计要求
- 部门接口当前已落地清单
- 结论章节对一期范围的更新说明

## 5. 验证结果

本次改动后已执行全量测试，并全部通过。

执行命令：

```powershell
.tools\apache-maven-3.9.6\bin\mvn.cmd test
```

测试结果：

- Tests run: `60`
- Failures: `0`
- Errors: `0`

## 6. 下一步建议

下一轮建议继续推进以下内容：

1. 飞书部门导入与 `external_id` 幂等更新流程
2. 部门导入与用户导入联动的差异比对
3. LDAP 全量同步 / 补偿同步接口
4. 部门启停与用户启停的联动策略细化
