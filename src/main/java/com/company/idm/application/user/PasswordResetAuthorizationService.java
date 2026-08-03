package com.company.idm.application.user;

import com.company.idm.common.exception.BizException;
import com.company.idm.common.exception.ErrorCodeConstants;
import com.company.idm.domain.user.User;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetAuthorizationService {

    public static final String RESET_ALL = "USER_PASSWORD_RESET_ALL";
    public static final String RESET_DIRECT = "USER_PASSWORD_RESET_DIRECT";
    public static final String RESET_TREE = "USER_PASSWORD_RESET_TREE";

    private final ReportingHierarchyService reportingHierarchyService;

    public PasswordResetAuthorizationService(ReportingHierarchyService reportingHierarchyService) {
        this.reportingHierarchyService = reportingHierarchyService;
    }

    public PasswordResetAuthorizationResult evaluate(
        User operator,
        User target,
        List<User> allUsers,
        Set<String> permissionCodes
    ) {
        if (operator == null || target == null || operator.getId() == null || target.getId() == null) {
            return PasswordResetAuthorizationResult.denied();
        }
        if (operator.getId().equals(target.getId())) {
            return PasswordResetAuthorizationResult.denied();
        }

        permissionCodes = permissionCodes == null ? Set.of() : permissionCodes;
        if (permissionCodes.contains(RESET_ALL)) {
            return PasswordResetAuthorizationResult.allowed(PasswordResetScope.ALL);
        }
        if (permissionCodes.contains(RESET_DIRECT) && reportingHierarchyService.isDirectSubordinate(operator, target)) {
            return PasswordResetAuthorizationResult.allowed(PasswordResetScope.DIRECT);
        }
        if (permissionCodes.contains(RESET_TREE) && reportingHierarchyService.isDescendant(operator, target, allUsers)) {
            return PasswordResetAuthorizationResult.allowed(PasswordResetScope.TREE);
        }
        return PasswordResetAuthorizationResult.denied();
    }

    public PasswordResetScope checkCanReset(
        User operator,
        User target,
        List<User> allUsers,
        Set<String> permissionCodes
    ) {
        PasswordResetAuthorizationResult result = evaluate(operator, target, allUsers, permissionCodes);
        if (!result.allowed()) {
            throw new BizException(ErrorCodeConstants.AUTH_FORBIDDEN, "无权限重置该用户密码");
        }
        return result.scope();
    }

}
