# 角色变更订阅推送 —— 接入与运维手册

> 适用版本：包含 Flyway `V54__role_change_subscription_push.sql` 的版本。
> 该版本起，原"组令牌 / 全局令牌管理"升级为"订阅管理"：创建订阅即自动生成订阅令牌、
> RabbitMQ 专属队列与专属消费账号，第三方不再轮询，由平台主动推送成员变更。
>
> **成员身份标识（V56 起）**：消息与 `/changes`、`/snapshot` 新增成员平台用户ID
> （`userId`，飞书ID；与 OpenLDAP `uid`、各平台登录名同链），用于按唯一标识精确匹配成员。

## 一、模式说明

平台对第三方只交付**成员关系**（哪些用户属于哪些角色），交付通道分两条：

| 通道 | 用途 | 说明 |
| --- | --- | --- |
| 订阅推送（主通道） | 角色成员授予/移除实时推送 | RabbitMQ 消息，按 `eventId` 幂等 |
| 开放供给接口（建账与兜底） | 首次建立初始账、异常对账 | `/api/v2/open/role-supply/snapshot` 与 `/changes`（V56 起响应含成员 `userId`） |

第三方拿到凭证后的标准流程：

1. 用订阅令牌调用 `/snapshot` 建立初始账；
2. 连接专属 MQ 队列，持续消费增量变更；
3. 如长时间离线或怀疑丢数，可继续用 `/changes` 增量对账（游标机制不变）。

## 二、开通订阅（平台侧操作）

1. 进入 **角色组管理**：组订阅选择角色组后进入 **订阅推送** 页签；全局订阅由平台管理员切换到页面右上角的 **全局订阅** 视图；
2. 点击 **创建订阅**：填写名称、说明，勾选需要推送的角色（支持全选）；
3. 创建成功后自动完成：
   - 生成订阅令牌（用于 `snapshot` / `changes` 鉴权）；
   - 创建 MQ 专属队列 `idm.sub.{订阅ID}` 与专属账号 `idm-sub-{订阅ID}`；
   - 按订阅角色逐条绑定路由键 `role.{roleCode}`；
4. 凭证对话框**一次性展示**全部连接信息，请立即妥善保存；
5. 之后如需重看凭证：列表 → **查看凭证** → 输入当前登录密码验证；
6. 其他操作：
   - **轮换**：重新生成令牌与 MQ 密码，旧凭证立即失效；
   - **停用**：第三方暂停接收（消息继续入队积压，不丢失），**启用**后继续消费；
   - **删除**：清理队列、账号、绑定与令牌，未消费消息随之丢失。

> 订阅为显式角色集合。新增角色不会自动进入已有订阅，需新建订阅或删除重建。

## 三、第三方连接参数

以订阅 ID 为 `3` 为例（实际值以创建/查看凭证时展示为准）：

| 参数 | 值 | 说明 |
| --- | --- | --- |
| AMQP 地址 | 例如 `10.0.0.8` | 由平台管理员配置；未配置时显示当前访问地址 |
| AMQP 端口 | `5672` | |
| vhost | `/` | |
| 队列 | `idm.sub.3` | 专属队列，仅本订阅账号可读 |
| 账号 | `idm-sub-3` | 专属账号，权限仅限读取自己的队列 |
| 密码 | 创建/轮换时展示 | 可通过"查看凭证"重看（需登录密码验证） |
| Exchange | `idm.role-change` | topic 型，平台自动维护绑定 |
| Routing Key | `role.{roleCode}` | 平台按订阅角色自动绑定 |

第三方**无需**任何 MQ 管理操作（不建队列、不建账号、不配绑定），只消费自己的队列即可。

## 四、消息格式

消息为 JSON，字段与 `/changes` 接口一致并附加 `eventId`：

```json
{
  "eventId": 1024,
  "changeType": "ADDED",
  "roleId": 12,
  "roleCode": "DEV_LEAD",
  "roleName": "研发负责人",
  "roleScope": "GROUP",
  "roleGroupId": 2,
  "memberName": "张三",
  "userId": "ou_6a1f...c9",
  "gmtCreate": "2026-09-16T10:20:30"
}
```

| 字段 | 说明 |
| --- | --- |
| `eventId` | 事件唯一编号（事件表主键，全局递增），**幂等去重键** |
| `changeType` | `ADDED` = 授予（成员加入角色）；`REMOVED` = 移除 |
| `roleId` / `roleCode` / `roleName` | 角色标识 |
| `roleScope` | `GLOBAL`（全局角色）/ `GROUP`（组角色） |
| `roleGroupId` | 组角色所属角色组 ID；全局角色为 `null` |
| `memberName` | 成员公开姓名（平台不推送工号、邮箱等隐私字段；成员匹配请用 `userId`） |
| `userId` | 成员平台用户ID（飞书ID），与 OpenLDAP `uid` 及各平台登录名一致，**唯一匹配键**；历史事件中成员已删除时为 `null` |
| `gmtCreate` | 变更发生时间（ISO-8601） |

消息属性：`contentType=application/json`、`contentEncoding=UTF-8`、`messageId=eventId`、持久化投递。

> `/snapshot` 响应中每个角色的 `memberNames` 与 `memberUserIds` 为**同源同序**数组（第 i 项一一对应）；
> `/changes` 响应字段与消息一致，仅以 `cursor` 代替 `eventId`。

## 五、可靠性语义

- **至少一次投递**：极端场景（网络抖动、确认超时）可能重复投递，第三方必须按 `eventId` 幂等；
- **保序**：平台单实例串行发布，同一队列内消息按事件发生顺序到达；消费端单消费者 + 手动 ack 可保持顺序处理；
- **自动重试**：平台推送失败会保序停止并在下一周期（默认 5 秒）重试，MQ 恢复后自动续传，消息不丢；
- **平台降级**：RabbitMQ 不可用时，角色管理、授权、LDAP 等全部功能不受影响，仅推送暂停并自动重试；
- **与 `/changes` 的关系**：推送与增量接口读同一份事件表，长期离线后可用 `/changes` 补齐对账。

## 六、消费示例

### 6.1 Java（Spring AMQP，手动 ack）

```java
@Component
public class RoleChangeConsumer {

    @RabbitListener(queues = "idm.sub.3", ackMode = "MANUAL")
    public void onMessage(RoleChangeMessage message,
                          Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            // 1. 幂等检查：eventId 已处理则直接 ack
            // 2. 业务处理：按 userId 匹配本地账号；ADDED 授予本地角色；REMOVED 撤销本地角色
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            // 处理失败：重新入队，稍后重试
            channel.basicNack(tag, false, true);
        }
    }
}
```

### 6.2 Python（pika）

```python
import json
import pika

connection = pika.BlockingConnection(pika.ConnectionParameters(
    host="<AMQP_ADDRESS>",
    port=5672,
    virtual_host="/",
    credentials=pika.PlainCredentials("idm-sub-3", "<MQ_PASSWORD>"),
))
channel = connection.channel()
channel.basic_qos(prefetch_count=10)


def handle(ch, method, properties, body):
    event = json.loads(body)
    # 1. 幂等检查：event["eventId"] 已处理则直接 ack
    # 2. 业务处理：按 userId 匹配本地账号；ADDED 授予本地角色；REMOVED 撤销本地角色
    ch.basic_ack(delivery_tag=method.delivery_tag)


channel.basic_consume(queue="idm.sub.3", on_message_callback=handle)
channel.start_consuming()
```

### 6.3 幂等建议

- 落库已处理的 `eventId`（唯一索引），重复消息直接忽略；
- 或按业务键覆盖：`(roleCode, userId)` + 最新状态，重复投递结果一致；
- 处理失败且暂时无法重试时，可选择 `basicNack(requeue=false)` 进入死信队列
  `idm.role-change.dlq`（运维在管理界面查看与人工重投）。

## 七、运维排障（平台侧）

### 7.1 日常检查

| 项目 | 方法 |
| --- | --- |
| 服务状态 | `docker compose ps`，`rabbitmq` 应为 `healthy` |
| 应用健康 | `curl http://服务器IP:8081/actuator/health`（含 `rabbit` 指示器） |
| 队列积压 | 管理界面 `http://服务器IP:15672/` → Queues → `idm.sub.{订阅ID}` 的 Ready 数 |
| 推送状态 | 应用日志搜索"角色变更事件推送失败"；数据库 `sys_role_membership_change.published_at` 是否有较大滞后 |
| 死信排查 | 管理界面 Queues → `idm.role-change.dlq` |

管理界面登录账号为 `RABBITMQ_USER` / `RABBITMQ_PASSWORD`（`.env` 中配置，仅平台运维使用）。

### 7.2 常见问题

| 现象 | 排查与处理 |
| --- | --- |
| 第三方收不到消息 | ① 订阅是否 `已启用`；② 队列 Ready 是否在增长（增长说明平台在发、第三方没消费）；③ 第三方连接参数（地址/端口/vhost/队列/账号密码）是否与凭证一致，账号错误会 403 |
| 第三方离线一段时间 | 消息在专属队列积压不丢，上线后自动续收；也可用 `/changes` 对账 |
| 消息重复 | 属预期（至少一次语义），第三方按 `eventId` 幂等 |
| 应用重启后消息补发 | 属正常：未确认发布的事件在恢复后 5 秒内自动续传 |
| 健康检查 `rabbit` 为 DOWN | RabbitMQ 暂时不可用，角色功能不受影响；确认 `rabbitmq` 容器状态，恢复后自动续传 |
| 凭证泄露 | 管理界面 **轮换**：重新生成令牌与 MQ 密码，旧凭证立即失效 |
| 删除订阅后误删数据 | 队列、账号、令牌已清理且不可恢复；需要时重新创建订阅并用 `/snapshot` 重建初始账 |

### 7.3 数据与回滚

- 订阅数据在 MySQL（`sys_role_push_subscription` / `sys_role_push_subscription_role`）；
- MQ 运行数据在 `data/rabbitmq`（随部署目录持久化）；
- 应用回滚只影响订阅推送功能，角色与 LDAP 功能不依赖 MQ；V56 起角色供应响应新增成员
  `userId` / `memberUserIds`（纯加法，旧字段不变；回滚旧版本时新增列保留但旧代码不读取，无影响）。

## 八、安全说明

- 专属账号权限只允许读取自己的队列（权限 pattern 精确匹配队列名），无法读取其他订阅或平台内部队列；
- 订阅令牌只可用于开放角色供给接口，不能调用任何管理或写入能力；
- 凭证完整值只在创建与轮换时一次性展示，重看需登录密码二次验证并留审计；
- 凭证不出现在任何列表、日志或审计详情中。
