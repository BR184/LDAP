package com.company.idm.application.rolegroup;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rolegroup.PushSubscription;
import com.company.idm.domain.rolegroup.RoleMembershipChange;
import com.company.idm.domain.rolegroup.RoleMembershipChangeRepository;
import com.company.idm.domain.rolegroup.RoleScopeVersionRepository;
import com.company.idm.domain.user.RoleSupplyMember;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.config.RoleSupplyProperties;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色供给查询服务：为订阅令牌提供完整快照与可恢复增量。
 *
 * <p>快照在范围版本行的共享锁下取得边界与内容，因此边界与事实来自同一一致视图；
 * 增量对角色组订阅按范围版本读取（版本顺序等于提交顺序），超出保留窗口时明确
 * 要求重建快照，而不是返回看似成功的残缺增量。全局订阅沿用事件游标语义，
 * 保持既有对外契约不变。
 */
@Service
@RequiredArgsConstructor
public class RoleSupplyApplicationService {

    private static final int MAX_CHANGE_PAGE_SIZE = 500;
    private static final int MAX_SNAPSHOT_PAGE_SIZE = 500;

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RoleMembershipChangeRepository changeRepository;
    private final RoleSupplyScopePolicy scopePolicy;
    private final RoleSupplySubscriptionResolver subscriptionResolver;
    private final RoleScopeVersionRepository scopeVersionRepository;
    private final RoleSupplyProperties supplyProperties;

    /**
     * 取得完整快照（单页，页大小取部署配置）。
     *
     * @param principal 订阅令牌主体
     * @return 快照，含范围身份、提交有序边界与完整性声明
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RoleSupplySnapshot snapshot(AuthenticatedUser principal) {
        return snapshot(principal, 1, 0);
    }

    /**
     * 取得完整快照的指定页。
     *
     * <p>所有批次属于同一冻结版本：页大小与页码只影响返回的角色切片，边界在锁下取得，
     * 消费方必须在接收完整（hasMore=false）后才执行撤权替换。
     *
     * @param principal 订阅令牌主体
     * @param page 页码，从 1 开始；小于 1 按 1 处理
     * @param size 页大小；小于等于 0 时取部署配置默认值
     * @return 快照页
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RoleSupplySnapshot snapshot(AuthenticatedUser principal, int page, int size) {
        PushSubscription subscription = subscriptionResolver.resolveEnabled(principal);
        Long groupId = subscriptionResolver.groupScopeOf(subscription);
        // 边界与内容必须来自同一读视图：使用普通读（与后续角色/成员读取同一快照），
        // 避免“锁定读取得新版本 + 普通读取得旧内容”的窗口；事务期间新提交的事件
        // 版本大于边界，会作为增量事件正常到达，不会遗漏。
        long boundary = groupId == null
            ? changeRepository.currentCursor()
            : scopeVersionRepository.currentVersion(groupId);
        List<Role> visibleRoles = scopePolicy.filterVisibleRoles(
            subscription.getSubjectType(),
            subscription.getSubjectId(),
            roleRepository.findAll()
        );
        int pageSize = size <= 0
            ? Math.max(1, Math.min(supplyProperties.getSnapshotPageSize(), MAX_SNAPSHOT_PAGE_SIZE))
            : Math.max(1, Math.min(size, MAX_SNAPSHOT_PAGE_SIZE));
        int currentPage = Math.max(1, page);
        int fromIndex = Math.min((currentPage - 1) * pageSize, visibleRoles.size());
        int toIndex = Math.min(fromIndex + pageSize, visibleRoles.size());
        List<RoleSupplyRoleSnapshot> roles = visibleRoles.subList(fromIndex, toIndex).stream()
            .map(this::toRoleSnapshot)
            .toList();
        return new RoleSupplySnapshot(
            boundary,
            subscription.getSubjectType().name(),
            groupId,
            boundary,
            true,
            currentPage,
            pageSize,
            toIndex < visibleRoles.size(),
            LocalDateTime.now(),
            roles
        );
    }

    /**
     * 读取增量变化。
     *
     * <p>角色组订阅以范围版本为检查点：只返回版本严格大于 since 的事件，按版本升序；
     * 若 since 已落到保留窗口之外，抛出游标过期错误要求重建快照。全局订阅沿用事件
     * 标识游标，语义与既有对外契约一致。
     *
     * @param principal 订阅令牌主体
     * @param cursor 检查点：组订阅为范围版本，全局订阅为事件标识
     * @param limit 单页条数上限
     * @return 增量结果与下一检查点
     */
    @Transactional(readOnly = true)
    public RoleSupplyChanges changes(AuthenticatedUser principal, long cursor, int limit) {
        PushSubscription subscription = subscriptionResolver.resolveEnabled(principal);
        if (cursor < 0) {
            throw new BizException("ROLE_SUPPLY_CURSOR_INVALID", "增量游标不能小于0");
        }
        int pageSize = Math.max(1, Math.min(limit, MAX_CHANGE_PAGE_SIZE));
        Long groupId = subscriptionResolver.groupScopeOf(subscription);
        if (groupId != null) {
            long earliest = changeRepository.earliestScopeVersion(groupId);
            if (cursor > 0 && earliest > 0 && cursor < earliest - 1) {
                throw new BizException("ROLE_SUPPLY_CURSOR_EXPIRED", "增量游标已超出事件保留窗口，请重建完整快照");
            }
            List<RoleMembershipChange> fetched =
                changeRepository.findAfterScopeVersion(groupId, cursor, pageSize + 1);
            boolean hasMore = fetched.size() > pageSize;
            List<RoleMembershipChange> changes = hasMore ? fetched.subList(0, pageSize) : fetched;
            long nextCursor = changes.isEmpty()
                ? Math.max(cursor, scopeVersionRepository.currentVersion(groupId))
                : changes.get(changes.size() - 1).scopeVersion();
            return new RoleSupplyChanges(nextCursor, hasMore, List.copyOf(changes));
        }
        List<RoleMembershipChange> fetched = changeRepository.findAfter(
            cursor,
            pageSize + 1,
            subscription.getSubjectType(),
            subscription.getSubjectId()
        );
        boolean hasMore = fetched.size() > pageSize;
        List<RoleMembershipChange> changes = hasMore ? fetched.subList(0, pageSize) : fetched;
        long nextCursor = changes.isEmpty()
            ? Math.max(cursor, changeRepository.currentCursor())
            : changes.get(changes.size() - 1).id();
        return new RoleSupplyChanges(nextCursor, hasMore, List.copyOf(changes));
    }

    private RoleSupplyRoleSnapshot toRoleSnapshot(Role role) {
        List<RoleSupplyMember> members = userRepository.findRoleSupplyMembersByRoleId(role.getId());
        return new RoleSupplyRoleSnapshot(
            role.getId(),
            role.getRoleCode(),
            role.getRoleName(),
            role.getRoleScope(),
            role.getRoleGroupId(),
            role.getStatus(),
            members.stream().map(RoleSupplyMember::realName).toList(),
            members.stream().map(RoleSupplyMember::platformUserId).toList()
        );
    }
}
