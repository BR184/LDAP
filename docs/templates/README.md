# 第三方系统 LDAP 模板目录

本目录用于沉淀第三方系统接入 OpenLDAP 的标准模板，目标是让 GitLab、Jenkins、Nexus、禅道等系统在同一套目录契约下接入，而不是各自维护一份分散的接入说明。

推荐使用顺序如下：

1. 先阅读 [ldap-common-contract.md](/C:/Users/Administrator/ldap/docs/templates/ldap-common-contract.md)，确认统一目录契约
2. 再选择目标系统模板：
   - [gitlab-ldap-template.md](/C:/Users/Administrator/ldap/docs/templates/gitlab-ldap-template.md)
   - [jenkins-ldap-template.md](/C:/Users/Administrator/ldap/docs/templates/jenkins-ldap-template.md)
   - [nexus-ldap-template.md](/C:/Users/Administrator/ldap/docs/templates/nexus-ldap-template.md)
   - [zentao-ldap-template.md](/C:/Users/Administrator/ldap/docs/templates/zentao-ldap-template.md)
3. 接入前执行 [ldap-precheck.md](/C:/Users/Administrator/ldap/docs/checklists/ldap-precheck.md)
4. 接入完成后执行 [ldap-acceptance.md](/C:/Users/Administrator/ldap/docs/checklists/ldap-acceptance.md)
5. 若联调失败或上线异常，按 [third-party-ldap-rollback.md](/C:/Users/Administrator/ldap/docs/runbooks/third-party-ldap-rollback.md) 回滚

统一要求如下：

- 登录字段固定为 `uid`
- 用户搜索根固定为 `ou=people,<baseDn>`
- 统一过滤器固定为 `(&(objectClass=inetOrgPerson)(uid={login})(employeeType=ENABLED))`
- 第三方系统本阶段只使用 LDAP 做认证，不使用 LDAP group 做统一授权
