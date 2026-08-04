package com.company.idm.application.rolegroup;

import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RoleSupplyScopePolicy {

    public List<Role> filterVisibleRoles(
        PersonalAccessTokenSubjectType subjectType,
        Long subjectId,
        List<Role> roles
    ) {
        if (subjectType == PersonalAccessTokenSubjectType.GLOBAL) {
            return roles == null ? List.of() : List.copyOf(roles);
        }
        if (subjectType != PersonalAccessTokenSubjectType.ROLE_GROUP || subjectId == null || roles == null) {
            return List.of();
        }
        return roles.stream()
            .filter(role -> role.getRoleScope() == RoleScope.GLOBAL
                || (role.getRoleScope() == RoleScope.GROUP && subjectId.equals(role.getRoleGroupId())))
            .toList();
    }
}
