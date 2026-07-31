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
        SELECT m.*
        FROM sys_menu m
        INNER JOIN sys_menu_permission mp ON mp.menu_id = m.id
        INNER JOIN sys_permission p ON p.id = mp.permission_id
        WHERE m.status = 1
          AND m.visible = 1
          AND p.status = 1
          AND p.permission_type = 'MENU'
          AND p.permission_code IN
          <foreach collection='permissionCodes' item='permissionCode' open='(' separator=',' close=')'>
            #{permissionCode}
          </foreach>
        ORDER BY m.sort_no ASC, m.id ASC
        </script>
        """)
    List<MenuDO> selectVisibleByPermissionCodes(@Param("permissionCodes") Set<String> permissionCodes);
}
