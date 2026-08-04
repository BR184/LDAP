package com.company.idm.domain.rbac;

/**
 * Defines whether a role is platform-internal, globally supplied, or owned by one role group.
 */
public enum RoleScope {
    GLOBAL,
    GROUP,
    SYSTEM
}
