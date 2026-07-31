package com.company.idm.application.user;

import com.company.idm.common.exception.BizException;
import com.company.idm.common.exception.ErrorCodeConstants;
import com.company.idm.domain.user.User;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserReadScopeService {

    public static final String USER_READ = "USER_READ";
    public static final String USER_READ_SELF_AND_SUBORDINATE_TREE = "USER_READ_SELF_AND_SUBORDINATE_TREE";

    private final ReportingHierarchyService reportingHierarchyService;

    public List<User> filterVisibleUsers(
        User operator,
        List<User> candidateUsers,
        List<User> organizationSnapshot,
        Set<String> permissionCodes
    ) {
        if (resolveScope(permissionCodes) == UserReadScope.ALL) {
            return candidateUsers == null ? List.of() : candidateUsers;
        }
        Set<Long> descendantUserIds = reportingHierarchyService.findDescendantUserIds(operator, organizationSnapshot);
        return (candidateUsers == null ? List.<User>of() : candidateUsers).stream()
            .filter(user -> user.getId() != null && (isOperator(operator, user) || descendantUserIds.contains(user.getId())))
            .toList();
    }

    public void checkCanReadUser(
        User operator,
        User target,
        List<User> organizationSnapshot,
        Set<String> permissionCodes
    ) {
        if (resolveScope(permissionCodes) == UserReadScope.ALL) {
            return;
        }
        if (!isOperator(operator, target) && !reportingHierarchyService.isDescendant(operator, target, organizationSnapshot)) {
            throw new BizException(ErrorCodeConstants.AUTH_FORBIDDEN, "无权限查看该用户");
        }
    }

    private boolean isOperator(User operator, User target) {
        return operator != null
            && operator.getId() != null
            && target != null
            && operator.getId().equals(target.getId());
    }

    private UserReadScope resolveScope(Set<String> permissionCodes) {
        Set<String> effectivePermissions = permissionCodes == null ? Set.of() : permissionCodes;
        if (effectivePermissions.contains(USER_READ)) {
            return UserReadScope.ALL;
        }
        if (effectivePermissions.contains(USER_READ_SELF_AND_SUBORDINATE_TREE)) {
            return UserReadScope.SUBORDINATE_TREE;
        }
        throw new BizException(ErrorCodeConstants.AUTH_FORBIDDEN, "无用户查询权限");
    }

    private enum UserReadScope {
        ALL,
        SUBORDINATE_TREE
    }
}
