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
        INNER JOIN sys_role_menu rm ON m.id = rm.menu_id
        INNER JOIN sys_role r ON rm.role_id = r.id
        WHERE m.status = 1 AND m.visible = 1
          AND r.status = 1
          AND r.role_code IN
          <foreach collection='roleCodes' item='roleCode' open='(' separator=',' close=')'>
              #{roleCode}
          </foreach>
        ORDER BY m.sort_no ASC, m.id ASC
        </script>
        """)
    List<MenuDO> selectByRoleCodes(@Param("roleCodes") Set<String> roleCodes);

    @Select("""
        SELECT COUNT(1)
        FROM sys_role_menu rm
        INNER JOIN sys_role r ON rm.role_id = r.id
        WHERE rm.menu_id = #{menuId}
          AND r.permission_level > #{minPermissionLevel}
        """)
    long countRoleBindingConflict(@Param("menuId") Long menuId, @Param("minPermissionLevel") Integer minPermissionLevel);
}
