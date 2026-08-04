package com.company.idm.application.rolegroup;

import com.company.idm.common.exception.BizException;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Keeps delegated group management and delegated assignment as one indivisible capability.
 */
@Component
public class DelegatedPermissionPairPolicy {

    public static final String ROLE_GROUP_MANAGE = "ROLE_GROUP_MANAGE";
    public static final String ROLE_GROUP_USER_ASSIGN = "ROLE_GROUP_USER_ASSIGN";
    public static final String ROLE_GROUP_MENU_VIEW = "MENU_VIEW_ROLE_GROUP_MANAGEMENT";

    public void validate(Set<String> permissionCodes) {
        Set<String> safeCodes = permissionCodes == null ? Set.of() : permissionCodes;
        boolean canManage = safeCodes.contains(ROLE_GROUP_MANAGE);
        boolean canAssign = safeCodes.contains(ROLE_GROUP_USER_ASSIGN);
        if (canManage != canAssign) {
            throw new BizException(
                "ROLE_GROUP_DELEGATION_PAIR_REQUIRED",
                "委派角色组管理与委派用户分配必须同时授予或同时撤销"
            );
        }
    }

    public boolean isDelegated(Set<String> permissionCodes) {
        validate(permissionCodes);
        Set<String> safeCodes = permissionCodes == null ? Set.of() : permissionCodes;
        return safeCodes.contains(ROLE_GROUP_MANAGE) && safeCodes.contains(ROLE_GROUP_USER_ASSIGN);
    }
}
