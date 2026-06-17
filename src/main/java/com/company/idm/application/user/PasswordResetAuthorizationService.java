package com.company.idm.application.user;

import com.company.idm.common.exception.BizException;
import com.company.idm.common.exception.ErrorCodeConstants;
import com.company.idm.domain.rbac.PermissionRepository;
import com.company.idm.domain.user.User;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetAuthorizationService {

    public static final String RESET_ALL = "USER_PASSWORD_RESET_ALL";
    public static final String RESET_DIRECT = "USER_PASSWORD_RESET_DIRECT";
    public static final String RESET_TREE = "USER_PASSWORD_RESET_TREE";

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final int MAX_TREE_DEPTH = 10;

    private final PermissionRepository permissionRepository;

    public PasswordResetAuthorizationService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
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
        if (permissionCodes.contains(RESET_DIRECT) && isDirectSubordinate(operator, target)) {
            return PasswordResetAuthorizationResult.allowed(PasswordResetScope.DIRECT);
        }
        if (permissionCodes.contains(RESET_TREE) && isTreeSubordinate(operator, target, allUsers)) {
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

    private boolean isDirectSubordinate(User operator, User target) {
        String operatorEmployeeNo = normalize(operator.getEmployeeNo());
        String targetLeaderRef = normalize(target.getLeaderRef());
        return !operatorEmployeeNo.isBlank() && operatorEmployeeNo.equals(targetLeaderRef);
    }

    private boolean isTreeSubordinate(User operator, User target, List<User> allUsers) {
        String operatorEmployeeNo = normalize(operator.getEmployeeNo());
        if (operatorEmployeeNo.isBlank() || allUsers == null || allUsers.isEmpty()) {
            return false;
        }

        Map<String, User> usersByEmployeeNo = new HashMap<>();
        for (User user : allUsers) {
            String employeeNo = normalize(user.getEmployeeNo());
            if (!employeeNo.isBlank()) {
                usersByEmployeeNo.putIfAbsent(employeeNo, user);
            }
        }

        String currentLeaderRef = normalize(target.getLeaderRef());
        Set<String> visitedEmployeeNos = new HashSet<>();
        for (int depth = 0; depth < MAX_TREE_DEPTH; depth++) {
            if (currentLeaderRef.isBlank()) {
                return false;
            }
            if (operatorEmployeeNo.equals(currentLeaderRef)) {
                return true;
            }
            if (!visitedEmployeeNos.add(currentLeaderRef)) {
                return false;
            }
            User leader = usersByEmployeeNo.get(currentLeaderRef);
            if (leader == null) {
                return false;
            }
            currentLeaderRef = normalize(leader.getLeaderRef());
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
