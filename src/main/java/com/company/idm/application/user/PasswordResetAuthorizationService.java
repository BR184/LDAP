package com.company.idm.application.user;

import com.company.idm.common.exception.BizException;
import com.company.idm.common.exception.ErrorCodeConstants;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.user.User;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetAuthorizationService {

    public static final String RESET_ALL = "USER_PASSWORD_RESET_ALL";
    public static final String RESET_DIRECT = "USER_PASSWORD_RESET_DIRECT";
    public static final String RESET_TREE = "USER_PASSWORD_RESET_TREE";

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private final PermissionRepository permissionRepository;
    private final ReportingHierarchyService reportingHierarchyService;

    public PasswordResetAuthorizationService(
        PermissionRepository permissionRepository,
        ReportingHierarchyService reportingHierarchyService
    ) {
        this.permissionRepository = permissionRepository;
        this.reportingHierarchyService = reportingHierarchyService;
    }

    public PasswordResetAuthorizationResult evaluate(User operator, User target, List<User> allUsers) {
        return evaluate(operator, target, allUsers, loadPermissionCodes(operator));
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

        Set<String> roleCodes = operator.getRoleCodes() == null ? Set.of() : operator.getRoleCodes();
        permissionCodes = permissionCodes == null ? Set.of() : permissionCodes;
        if (roleCodes.contains(SUPER_ADMIN) || permissionCodes.contains(RESET_ALL)) {
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

    public PasswordResetScope checkCanReset(User operator, User target, List<User> allUsers) {
        PasswordResetAuthorizationResult result = evaluate(operator, target, allUsers);
        if (!result.allowed()) {
            throw new BizException(ErrorCodeConstants.AUTH_FORBIDDEN, "无权限重置该用户密码");
        }
        return result.scope();
    }

    public Set<String> loadPermissionCodes(User operator) {
        if (operator == null || operator.getUserId() == null || operator.getUserId().isBlank()) {
            return Set.of();
        }
        return permissionRepository.findPermissionCodesByUserId(operator.getUserId());
    }

}
