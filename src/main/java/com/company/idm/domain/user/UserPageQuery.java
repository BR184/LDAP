package com.company.idm.domain.user;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V2 用户分页查询规格（含部门规则筛选）。
 *
 * @param departmentRules 归一化后的部门规则；为空表示不限部门
 * @param pageNum         页码（从 1 开始）
 * @param pageSize        每页条数（上限 100）
 * @param orderBy         排序白名单字段（id/userId/realName/employeeNo/deptCode/gmtCreate/gmtModified）
 * @param orderDir        排序方向 asc/desc
 */
public record UserPageQuery(
    String keyword,
    String userId,
    String realName,
    String employeeNo,
    String mobile,
    String email,
    String intranetEmail,
    String jobTitle,
    Boolean accessAllowed,
    String employmentStatus,
    String accountStatus,
    String sourceType,
    List<String> roleCodes,
    LocalDateTime createdStart,
    LocalDateTime createdEnd,
    List<DepartmentRule> departmentRules,
    long pageNum,
    long pageSize,
    String orderBy,
    String orderDir
) {

    /**
     * 归一化后的部门规则。primaryCodes/partTimeCodes 已展开为具体 dept_code 集合。
     *
     * @param exclude       true=排除规则（mode=exclude），false=包含规则（mode=include）
     * @param unassigned    未归属部门规则（无主部门且无兼职）
     * @param primaryCodes  命中主部门（sys_user.dept_code）的 dept_code 集合
     * @param partTimeCodes 命中兼职部门（sys_user_part_time_department）的 dept_code 集合
     */
    public record DepartmentRule(
        boolean exclude,
        boolean unassigned,
        List<String> primaryCodes,
        List<String> partTimeCodes
    ) {
    }
}
