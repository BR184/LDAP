package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.PersonalAccessTokenPermissionDO;
import com.company.idm.infrastructure.persistence.record.PersonalAccessTokenPermissionRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PersonalAccessTokenPermissionMapper extends BaseMapper<PersonalAccessTokenPermissionDO> {

    @Select("""
        <script>
        SELECT relation.token_id AS tokenId,
               permission.id AS permissionId,
               permission.permission_code AS permissionCode,
               permission.permission_name AS permissionName,
               permission.resource_path AS resourcePath,
               permission.action AS action
        FROM sys_personal_access_token_permission relation
        INNER JOIN sys_permission permission ON permission.id = relation.permission_id
        WHERE relation.token_id IN
        <foreach collection="tokenIds" item="tokenId" open="(" separator="," close=")">
            #{tokenId}
        </foreach>
        ORDER BY relation.token_id, permission.sort_no, permission.id
        </script>
        """)
    List<PersonalAccessTokenPermissionRecord> selectPermissionRecords(
        @Param("tokenIds") List<Long> tokenIds
    );
}
