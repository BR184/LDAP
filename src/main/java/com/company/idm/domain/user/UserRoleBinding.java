package com.company.idm.domain.user;

/**
 * 表示用户与角色之间的绑定关系。
 */
public record UserRoleBinding(String username, String roleCode) {
}

