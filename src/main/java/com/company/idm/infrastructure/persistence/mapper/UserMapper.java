package com.company.idm.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.company.idm.domain.user.UserPageQuery;
import com.company.idm.infrastructure.persistence.dataobject.UserDO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 提供用户表的 MyBatis-Plus 访问能力。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {

    @Select("SELECT * FROM sys_user WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    UserDO selectByIdForUpdate(@Param("id") Long id);

    /**
     * V2 用户分页查询（复杂查询在 resources/mapper/UserMapper.xml）。
     * 第一个参数为 IPage，PaginationInnerInterceptor 自动补 count + limit/offset。
     */
    IPage<UserDO> selectV2UserPage(
        IPage<UserDO> page,
        @Param("userId") String userId,
        @Param("keyword") String keyword,
        @Param("realName") String realName,
        @Param("employeeNo") String employeeNo,
        @Param("mobile") String mobile,
        @Param("email") String email,
        @Param("intranetEmail") String intranetEmail,
        @Param("jobTitle") String jobTitle,
        @Param("accessAllowed") Boolean accessAllowed,
        @Param("employmentStatus") String employmentStatus,
        @Param("accountStatus") String accountStatus,
        @Param("sourceType") String sourceType,
        @Param("roleCodes") List<String> roleCodes,
        @Param("createdStart") LocalDateTime createdStart,
        @Param("createdEnd") LocalDateTime createdEnd,
        @Param("includeRules") List<UserPageQuery.DepartmentRule> includeRules,
        @Param("excludeRules") List<UserPageQuery.DepartmentRule> excludeRules,
        @Param("visibleUserIds") List<Long> visibleUserIds,
        @Param("orderBy") String orderBy,
        @Param("orderDir") String orderDir
    );
}
