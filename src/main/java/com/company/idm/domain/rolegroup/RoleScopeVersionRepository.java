package com.company.idm.domain.rolegroup;

/**
 * 角色供给范围的提交有序版本仓储。
 *
 * <p>版本以角色组为范围单位：同一组内的写入通过行锁串行化，因此版本顺序等于事务提交顺序，
 * 可作为快照边界与增量恢复检查点的安全依据；不同组之间版本相互独立推进。
 * 所有方法都必须在业务事务内调用，禁止在事务外分配版本。
 */
public interface RoleScopeVersionRepository {

    /**
     * 在当前事务内为指定范围分配下一个版本。
     *
     * <p>实现必须对该范围版本行加排他锁后再递增，使同组并发写入串行提交；
     * 范围行不存在时先创建。事务回滚时版本不会被消费方观察到。
     *
     * @param scopeId 角色组 ID，不可为空
     * @return 分配到的版本号，从 1 开始单调递增
     */
    long nextVersion(Long scopeId);

    /**
     * 读取范围当前已提交版本头（不加锁）。
     *
     * @param scopeId 角色组 ID
     * @return 当前版本头；范围尚无版本行时返回 0
     */
    long currentVersion(Long scopeId);

    /**
     * 在事务内锁定并读取范围版本头，用于快照取得一致边界。
     *
     * <p>持锁期间同组的写入被阻塞，因此随后在同一事务读取的角色与成员事实
     * 与该版本严格对应，不会出现“边界落后于内容”或“内容落后于边界”。
     *
     * @param scopeId 角色组 ID
     * @return 锁定时刻的版本头
     */
    long lockCurrentVersion(Long scopeId);
}
