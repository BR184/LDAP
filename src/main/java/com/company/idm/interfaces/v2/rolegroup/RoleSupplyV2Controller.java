package com.company.idm.interfaces.v2.rolegroup;

import com.company.idm.application.rolegroup.RoleSupplyApplicationService;
import com.company.idm.application.rolegroup.RoleSupplyChanges;
import com.company.idm.application.rolegroup.RoleSupplyContext;
import com.company.idm.application.rolegroup.RoleSupplyRoleSnapshot;
import com.company.idm.application.rolegroup.RoleSupplySnapshot;
import com.company.idm.application.rolegroup.SubscriptionApplicationService;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.rolegroup.RoleMembershipChange;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 角色供应开放接口（面向第三方系统，使用订阅令牌鉴权）。
 *
 * <p>接入方只需持有订阅令牌即可完成接入：先取上下文获得来源身份、订阅与角色组身份、
 * 协议能力与专属连接凭据，再取完整快照建立事实基线，之后依靠推送与按检查点的增量恢复
 * 跟随变化，正常态无需轮询。V1 接口保持既有契约不变，本控制器独立定义响应结构。
 */
@RestController
@RequestMapping("/api/v2/open/role-supply")
@RequiredArgsConstructor
@PreAuthorize("@credentialAccessService.isRoleSupplyToken(authentication)")
public class RoleSupplyV2Controller {

    private final RoleSupplyApplicationService applicationService;
    private final SubscriptionApplicationService subscriptionApplicationService;

    /**
     * 取得订阅上下文：接入方凭令牌一次性获取接入所需信息。
     *
     * <p>响应含专属连接凭据，禁止缓存；订阅停用或撤销时只返回非敏感状态。
     */
    @GetMapping("/context")
    public ContextResponse context(
        @AuthenticationPrincipal AuthenticatedUser principal,
        HttpServletRequest servletRequest,
        HttpServletResponse servletResponse
    ) {
        // 响应体含专属连接凭据（MQ 账号与密码）：禁止任何缓存，与订阅管理接口同一策略。
        disableSecretCaching(servletResponse);
        RoleSupplyContext context = subscriptionApplicationService.contextForToken(
            principal, servletRequest.getServerName());
        return ContextResponse.from(context);
    }

    /** 秘密响应统一禁止缓存：no-store + no-cache + Expires 0。 */
    private void disableSecretCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }

    /**
     * 取得完整快照（可分页）。
     *
     * <p>所有分页属于同一冻结版本；消费方必须在 hasMore=false 后才执行撤权替换。
     *
     * @param page 页码，从 1 开始
     * @param size 页大小；0 表示使用部署默认值
     */
    @GetMapping("/snapshot")
    public ApiResponseV2<SnapshotResponse> snapshot(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "0") int size,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        RoleSupplySnapshot snapshot = applicationService.snapshot(principal, page, size);
        return ApiResponseV2.ok(SnapshotResponse.from(snapshot));
    }

    /**
     * 读取增量变化。
     *
     * <p>角色组订阅的 cursor 是范围提交有序版本；全局订阅沿用事件标识游标。
     * 游标超出保留窗口时返回明确错误，要求重建完整快照。
     */
    @GetMapping("/changes")
    public ApiResponseV2<ChangesResponse> changes(
        @RequestParam(defaultValue = "0") long cursor,
        @RequestParam(defaultValue = "200") int limit,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        RoleSupplyChanges result = applicationService.changes(principal, cursor, limit);
        return ApiResponseV2.ok(ChangesResponse.from(result));
    }

    /** 上下文响应：秘密字段仅供接入方后端使用，不得下发到浏览器或写入日志。 */
    public record ContextResponse(
        int protocolVersion,
        String sourceId,
        SubscriptionInfo subscription,
        RoleGroupInfo roleGroup,
        MqConnection mq,
        List<RoleInfo> roles
    ) {

        /** 由应用层上下文构造响应。 */
        public static ContextResponse from(RoleSupplyContext context) {
            return new ContextResponse(
                context.protocolVersion(),
                context.sourceId(),
                context.subscription() == null ? null : new SubscriptionInfo(
                    context.subscription().id(),
                    context.subscription().name(),
                    context.subscription().status(),
                    context.subscription().scopeMode(),
                    context.subscription().configVersion()
                ),
                context.roleGroup() == null ? null : new RoleGroupInfo(
                    context.roleGroup().id(),
                    context.roleGroup().name()
                ),
                context.mq() == null ? null : new MqConnection(
                    context.mq().host(),
                    context.mq().port(),
                    context.mq().vhost(),
                    context.mq().queue(),
                    context.mq().username(),
                    context.mq().password()
                ),
                context.roles() == null ? List.of() : context.roles().stream()
                    .map(role -> new RoleInfo(role.id(), role.code(), role.name(), role.status()))
                    .toList()
            );
        }
    }

    /** 订阅身份信息。 */
    public record SubscriptionInfo(
        Long id,
        String name,
        String status,
        String scopeMode,
        Long configVersion
    ) {
    }

    /** 角色组身份信息。 */
    public record RoleGroupInfo(Long id, String name) {
    }

    /** 专属连接凭据。 */
    public record MqConnection(
        String host,
        int port,
        String vhost,
        String queue,
        String username,
        String password
    ) {
    }

    /** 范围内角色目录项。 */
    public record RoleInfo(Long id, String code, String name, Integer status) {
    }

    /** 快照响应。 */
    public record SnapshotResponse(
        long snapshotCursor,
        String scopeType,
        Long roleGroupId,
        long scopeVersion,
        boolean complete,
        int page,
        int pageSize,
        boolean hasMore,
        LocalDateTime generatedAt,
        List<RoleResponse> roles
    ) {

        /** 由应用层快照构造响应。 */
        public static SnapshotResponse from(RoleSupplySnapshot snapshot) {
            return new SnapshotResponse(
                snapshot.snapshotCursor(),
                snapshot.scopeType(),
                snapshot.roleGroupId(),
                snapshot.scopeVersion(),
                snapshot.complete(),
                snapshot.page(),
                snapshot.pageSize(),
                snapshot.hasMore(),
                snapshot.generatedAt(),
                snapshot.roles().stream().map(RoleResponse::from).toList()
            );
        }
    }

    /** 快照中的角色事实。 */
    public record RoleResponse(
        Long roleId,
        String roleCode,
        String roleName,
        RoleScope roleScope,
        Long roleGroupId,
        Integer roleStatus,
        List<String> memberNames,
        List<String> memberUserIds
    ) {

        /** 由应用层角色快照构造响应。 */
        public static RoleResponse from(RoleSupplyRoleSnapshot role) {
            return new RoleResponse(
                role.roleId(),
                role.roleCode(),
                role.roleName(),
                role.roleScope(),
                role.roleGroupId(),
                role.roleStatus(),
                role.memberNames(),
                role.memberUserIds()
            );
        }
    }

    /** 增量响应。 */
    public record ChangesResponse(long nextCursor, boolean hasMore, List<ChangeResponse> changes) {

        /** 由应用层增量结果构造响应。 */
        public static ChangesResponse from(RoleSupplyChanges result) {
            return new ChangesResponse(
                result.nextCursor(),
                result.hasMore(),
                result.changes().stream().map(ChangeResponse::from).toList()
            );
        }
    }

    /** 增量事件；payload 为原始 JSON 文本，由消费方按事件类型解释。 */
    public record ChangeResponse(
        Long eventId,
        String eventType,
        Long roleId,
        String roleCode,
        String roleName,
        RoleScope roleScope,
        Long roleGroupId,
        Long scopeVersion,
        String memberName,
        String userId,
        String changeType,
        String payload,
        Long subscriptionId,
        Long controlVersion,
        LocalDateTime changedAt
    ) {

        /** 由领域事件构造响应。 */
        public static ChangeResponse from(RoleMembershipChange change) {
            return new ChangeResponse(
                change.id(),
                change.eventType() == null ? null : change.eventType().name(),
                change.roleId(),
                change.roleCode(),
                change.roleName(),
                change.roleScope(),
                change.roleGroupId(),
                change.scopeVersion(),
                change.memberName(),
                change.memberUserId(),
                change.changeType(),
                change.payload(),
                change.subscriptionId(),
                change.controlVersion(),
                change.gmtCreate()
            );
        }
    }
}
