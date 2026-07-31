package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.idm.infrastructure.persistence.dataobject.MenuDO;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 提供菜单表的 MyBatis-Plus 访问能力。
 */
@Mapper
public interface MenuMapper extends BaseMapper<MenuDO> {

    @Select("""
        <script>
        SELECT DISTINCT m.*
        FROM sys_menu m
        LEFT JOIN sys_role_menu rm ON m.id = rm.menu_id
        LEFT JOIN sys_role r ON rm.role_id = r.id
        LEFT JOIN sys_menu_permission mp ON m.id = mp.menu_id
        LEFT JOIN sys_permission p ON mp.permission_id = p.id
        WHERE m.status = 1 AND m.visible = 1
          AND (
            <trim prefixOverrides="OR">
              <if test="roleCodes != null and !roleCodes.isEmpty()">
                (r.status = 1
                  AND r.role_code IN
                  <foreach collection='roleCodes' item='roleCode' open='(' separator=',' close=')'>
                    #{roleCode}
                  </foreach>
                  AND NOT EXISTS (
                    SELECT 1
                    FROM sys_menu_permission configured
                    WHERE configured.menu_id = m.id
                  ))
              </if>
              <if test="permissionCodes != null and !permissionCodes.isEmpty()">
                OR (p.status = 1
                  AND p.permission_code IN
                  <foreach collection='permissionCodes' item='permissionCode' open='(' separator=',' close=')'>
                    #{permissionCode}
                  </foreach>)
              </if>
            </trim>
          )
        ORDER BY m.sort_no ASC, m.id ASC
        </script>
        """)
    List<MenuDO> selectByAccess(
        @Param("roleCodes") Set<String> roleCodes,
        @Param("permissionCodes") Set<String> permissionCodes
    );

    @Select("""
        SELECT COUNT(1)
        FROM sys_role_menu rm
        INNER JOIN sys_role r ON rm.role_id = r.id
        WHERE rm.menu_id = #{menuId}
          AND r.permission_level > #{minPermissionLevel}
        """)
    long countRoleBindingConflict(@Param("menuId") Long menuId, @Param("minPermissionLevel") Integer minPermissionLevel);
}
