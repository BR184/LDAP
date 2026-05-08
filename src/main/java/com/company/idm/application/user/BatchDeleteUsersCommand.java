package com.company.idm.application.user;

import java.util.List;

/**
 * 封装批量删除用户时的应用层命令参数。
 */
public record BatchDeleteUsersCommand(
    List<Long> userIds,
    String userIdKeyword,
    String deptNameKeyword,
    Integer statusCode,
    String operator
) {

    public BatchDeleteUsersCommand(List<Long> userIds, String operator) {
        this(userIds, null, null, null, operator);
    }
}
