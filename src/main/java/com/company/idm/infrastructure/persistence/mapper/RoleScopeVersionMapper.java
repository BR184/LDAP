package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.RoleScopeVersionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RoleScopeVersionMapper extends BaseMapper<RoleScopeVersionDO> {

    /** 排他锁定范围版本行，使同组写入串行提交；行不存在时返回 null。 */
    @Select("SELECT scope_id AS scopeId, committed_version AS committedVersion "
        + "FROM sys_role_scope_version WHERE scope_id = #{scopeId} FOR UPDATE")
    RoleScopeVersionDO selectForUpdate(@Param("scopeId") Long scopeId);

    /** 共享锁定范围版本行，用于快照取得一致边界（阻塞同组写入直到快照事务结束）。 */
    @Select("SELECT scope_id AS scopeId, committed_version AS committedVersion "
        + "FROM sys_role_scope_version WHERE scope_id = #{scopeId} LOCK IN SHARE MODE")
    RoleScopeVersionDO selectForShare(@Param("scopeId") Long scopeId);

    /** 递增范围版本头，返回受影响行数。 */
    @Update("UPDATE sys_role_scope_version SET committed_version = #{version}, "
        + "gmt_modified = NOW(3) WHERE scope_id = #{scopeId}")
    int updateVersion(@Param("scopeId") Long scopeId, @Param("version") Long version);
}
