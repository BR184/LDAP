package com.company.idm.domain.rbac;

public record RolePolicy(String roleCode, String resourcePath, String action) {
}

