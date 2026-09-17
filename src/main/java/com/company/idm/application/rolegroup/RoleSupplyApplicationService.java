package com.company.idm.application.rolegroup;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rolegroup.RoleMembershipChange;
import com.company.idm.domain.rolegroup.RoleMembershipChangeRepository;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.infrastructure.security.CredentialType;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleSupplyApplicationService {

    private static final int MAX_CHANGE_PAGE_SIZE = 500;

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RoleMembershipChangeRepository changeRepository;
    private final RoleSupplyScopePolicy scopePolicy;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RoleSupplySnapshot snapshot(AuthenticatedUser principal) {
        PersonalAccessTokenSubjectType subjectType = requireSupplySubject(principal);
        long snapshotCursor = changeRepository.currentCursor();
        List<Role> visibleRoles = scopePolicy.filterVisibleRoles(
            subjectType,
            principal.tokenSubjectId(),
            roleRepository.findAll()
        );
        List<RoleSupplyRoleSnapshot> roles = visibleRoles.stream()
            .map(role -> new RoleSupplyRoleSnapshot(
                role.getId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getRoleScope(),
                role.getRoleGroupId(),
                userRepository.findPublicUsersByRoleId(role.getId()).stream()
                    .map(com.company.idm.domain.user.PublicUser::realName)
                    .toList()
            ))
            .toList();
        return new RoleSupplySnapshot(snapshotCursor, LocalDateTime.now(), roles);
    }

    @Transactional(readOnly = true)
    public RoleSupplyChanges changes(AuthenticatedUser principal, long cursor, int limit) {
        PersonalAccessTokenSubjectType subjectType = requireSupplySubject(principal);
        if (cursor < 0) {
            throw new BizException("ROLE_SUPPLY_CURSOR_INVALID", "增量游标不能小于0");
        }
        int pageSize = Math.max(1, Math.min(limit, MAX_CHANGE_PAGE_SIZE));
        List<RoleMembershipChange> fetched = changeRepository.findAfter(
            cursor,
            pageSize + 1,
            subjectType,
            principal.tokenSubjectId()
        );
        boolean hasMore = fetched.size() > pageSize;
        List<RoleMembershipChange> changes = hasMore ? fetched.subList(0, pageSize) : fetched;
        long nextCursor = changes.isEmpty()
            ? Math.max(cursor, changeRepository.currentCursor())
            : changes.get(changes.size() - 1).id();
        return new RoleSupplyChanges(nextCursor, hasMore, List.copyOf(changes));
    }

    private PersonalAccessTokenSubjectType requireSupplySubject(AuthenticatedUser principal) {
        if (principal == null
            || principal.credentialType() != CredentialType.PERSONAL_ACCESS_TOKEN
            || principal.tokenSubjectType() == null
            || principal.tokenSubjectType() == PersonalAccessTokenSubjectType.USER) {
            throw new BizException("ROLE_SUPPLY_TOKEN_REQUIRED", "请使用订阅令牌访问");
        }
        if (principal.tokenSubjectType() == PersonalAccessTokenSubjectType.ROLE_GROUP
            && principal.tokenSubjectId() == null) {
            throw new BizException("ROLE_SUPPLY_TOKEN_INVALID", "角色组订阅令牌缺少固定作用域");
        }
        return principal.tokenSubjectType();
    }
}
