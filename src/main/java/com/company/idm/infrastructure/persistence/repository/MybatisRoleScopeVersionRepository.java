package com.company.idm.infrastructure.persistence.repository;

import com.company.idm.domain.rolegroup.RoleScopeVersionRepository;
import com.company.idm.infrastructure.persistence.dataobject.RoleScopeVersionDO;
import com.company.idm.infrastructure.persistence.mapper.RoleScopeVersionMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * 范围版本仓储实现：以行锁保证同组提交有序。
 *
 * <p>分配版本时先对版本行加排他锁再递增，锁在事务提交时释放，因此后提交的事务
 * 必然取得更大的版本号；并发首个写入者可能同时尝试插入版本行，靠唯一主键冲突
 * 兜底后重新加锁读取，不依赖任何全局自增序列。
 */
@Repository
@RequiredArgsConstructor
public class MybatisRoleScopeVersionRepository implements RoleScopeVersionRepository {

    private final RoleScopeVersionMapper mapper;

    @Override
    public long nextVersion(Long scopeId) {
        requireScopeId(scopeId);
        RoleScopeVersionDO locked = lockRow(scopeId);
        long next = (locked.getCommittedVersion() == null ? 0L : locked.getCommittedVersion()) + 1;
        mapper.updateVersion(scopeId, next);
        return next;
    }

    @Override
    public long currentVersion(Long scopeId) {
        requireScopeId(scopeId);
        RoleScopeVersionDO row = mapper.selectById(scopeId);
        return row == null || row.getCommittedVersion() == null ? 0L : row.getCommittedVersion();
    }

    @Override
    public long lockCurrentVersion(Long scopeId) {
        requireScopeId(scopeId);
        RoleScopeVersionDO row = mapper.selectForShare(scopeId);
        if (row == null) {
            // 范围尚未产生任何事件：创建版本行并共享锁定，保证快照边界与后续写入互斥。
            insertIfAbsent(scopeId);
            row = mapper.selectForShare(scopeId);
        }
        return row == null || row.getCommittedVersion() == null ? 0L : row.getCommittedVersion();
    }

    private RoleScopeVersionDO lockRow(Long scopeId) {
        RoleScopeVersionDO locked = mapper.selectForUpdate(scopeId);
        if (locked != null) {
            return locked;
        }
        insertIfAbsent(scopeId);
        RoleScopeVersionDO retried = mapper.selectForUpdate(scopeId);
        if (retried == null) {
            throw new IllegalStateException("无法锁定角色供给范围版本行: scopeId=" + scopeId);
        }
        return retried;
    }

    private void insertIfAbsent(Long scopeId) {
        RoleScopeVersionDO row = new RoleScopeVersionDO();
        row.setScopeId(scopeId);
        row.setCommittedVersion(0L);
        row.setGmtModified(LocalDateTime.now());
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException ignored) {
            // 并发首次写入：另一事务已创建版本行，重新加锁读取即可。
        }
    }

    private void requireScopeId(Long scopeId) {
        if (scopeId == null) {
            throw new IllegalArgumentException("范围版本必须绑定角色组标识");
        }
    }
}
