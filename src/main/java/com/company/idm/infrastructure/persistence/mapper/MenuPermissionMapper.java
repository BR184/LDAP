package com.company.idm.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MenuPermissionMapper {

    @Select("SELECT permission_id FROM sys_menu_permission WHERE menu_id = #{menuId}")
    Long selectPermissionIdByMenuId(@Param("menuId") Long menuId);

    @Insert("""
        INSERT INTO sys_menu_permission (menu_id, permission_id, creator)
        VALUES (#{menuId}, #{permissionId}, #{creator})
        """)
    void insert(
        @Param("menuId") Long menuId,
        @Param("permissionId") Long permissionId,
        @Param("creator") String creator
    );

    @Delete("DELETE FROM sys_menu_permission WHERE menu_id = #{menuId}")
    void deleteByMenuId(@Param("menuId") Long menuId);
}
