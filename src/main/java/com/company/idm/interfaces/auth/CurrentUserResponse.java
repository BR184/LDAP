package com.company.idm.interfaces.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Set;

/**
 * 封装当前登录用户信息的响应数据。
 */
public record CurrentUserResponse(
    Long id,
    String userId,
    String realName,
    String email,
    String intranetEmail,
    String mobile,
    String employeeNo,
    String jobTitle,
    @JsonInclude(JsonInclude.Include.ALWAYS) String deptCode,
    @JsonInclude(JsonInclude.Include.ALWAYS) String deptName,
    @JsonInclude(JsonInclude.Include.ALWAYS) String departmentPath,
    Set<String> roleCodes
) {
}
