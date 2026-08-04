package com.company.idm.domain.rolegroup;

import java.util.List;
import java.util.Optional;

public interface RoleGroupRepository {

    RoleGroup save(RoleGroup roleGroup);

    Optional<RoleGroup> findById(Long groupId);

    void lockById(Long groupId);

    List<RoleGroup> findAll();

    List<RoleGroup> findByMemberUserId(Long userId);

    Optional<RoleGroupMember> findMember(Long groupId, Long userId);

    List<RoleGroupMember> findMembers(Long groupId);

    void saveMember(RoleGroupMember member, String operator);

    void removeMember(Long groupId, Long userId);

    long countOwners(Long groupId);

    void delete(Long groupId);
}
