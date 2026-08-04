package com.company.idm.application.rolegroup;

import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.rolegroup.RoleGroupMemberRole;
import java.util.List;

public record PersonalRoleContext(List<PersonalRole> roles, List<ParticipatingRoleGroup> roleGroups) {

    public record PersonalRole(
        Long id,
        String roleCode,
        String roleName,
        RoleScope roleScope,
        Long roleGroupId,
        String roleGroupName
    ) {
    }

    public record ParticipatingRoleGroup(Long id, String groupName, RoleGroupMemberRole memberRole) {
    }
}
