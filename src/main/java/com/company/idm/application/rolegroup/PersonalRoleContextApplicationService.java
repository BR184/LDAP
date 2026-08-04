package com.company.idm.application.rolegroup;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rolegroup.RoleGroup;
import com.company.idm.domain.rolegroup.RoleGroupMember;
import com.company.idm.domain.rolegroup.RoleGroupRepository;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PersonalRoleContextApplicationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleGroupRepository roleGroupRepository;

    public PersonalRoleContext get(AuthenticatedUser principal) {
        if (principal == null || principal.id() == null) {
            throw new BizException("AUTH_FORBIDDEN", "当前身份不能查看个人角色信息");
        }
        Map<Long, RoleGroup> groupsById = roleGroupRepository.findAll().stream()
            .collect(Collectors.toMap(RoleGroup::getId, Function.identity()));
        var roles = roleRepository.findByIds(userRepository.findRoleIdsByUserId(principal.id())).stream()
            .map(role -> new PersonalRoleContext.PersonalRole(
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getRoleScope(),
                role.getRoleGroupId(),
                role.getRoleGroupId() == null || !groupsById.containsKey(role.getRoleGroupId())
                    ? null
                    : groupsById.get(role.getRoleGroupId()).getGroupName()
            ))
            .toList();
        var groups = roleGroupRepository.findByMemberUserId(principal.id()).stream()
            .map(group -> new PersonalRoleContext.ParticipatingRoleGroup(
                group.getId(),
                group.getGroupName(),
                roleGroupRepository.findMember(group.getId(), principal.id())
                    .map(RoleGroupMember::memberRole)
                    .orElse(null)
            ))
            .toList();
        return new PersonalRoleContext(roles, groups);
    }
}
