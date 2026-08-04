package com.company.idm.application.rolegroup;

import com.company.idm.common.exception.BizException;
import com.company.idm.domain.audit.AuditLog;
import com.company.idm.domain.audit.AuditLogRepository;
import com.company.idm.application.rbac.PolicyRefreshService;
import com.company.idm.common.enums.EmploymentStatus;
import com.company.idm.domain.rbac.Role;
import com.company.idm.domain.rbac.RoleRepository;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.rolegroup.RoleGroup;
import com.company.idm.domain.rolegroup.RoleGroupMember;
import com.company.idm.domain.rolegroup.RoleGroupMemberRole;
import com.company.idm.domain.rolegroup.RoleGroupRepository;
import com.company.idm.domain.user.PublicUser;
import com.company.idm.domain.user.User;
import com.company.idm.domain.user.UserRepository;
import com.company.idm.domain.token.PersonalAccessTokenRepository;
import com.company.idm.domain.token.PersonalAccessTokenSubjectType;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleGroupApplicationService {

    private static final int GROUP_ROLE_PERMISSION_LEVEL = 999;
    private static final int MAX_GROUP_NAME_LENGTH = 128;
    private static final int MAX_REMARK_LENGTH = 256;
    private static final int PUBLIC_USER_SEARCH_LIMIT = 30;
    private static final Set<String> PLATFORM_ADMIN_ROLES = Set.of("ADMIN", "SUPER_ADMIN");
    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    private final RoleGroupRepository roleGroupRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PolicyRefreshService policyRefreshService;
    private final RoleGroupAuthorizationService authorizationService;
    private final PersonalAccessTokenRepository tokenRepository;

    public List<RoleGroupView> listGroups(AuthenticatedUser principal) {
        authorizationService.requireDelegated(principal);
        List<RoleGroup> groups = authorizationService.isPlatformAdmin(principal)
            ? roleGroupRepository.findAll()
            : roleGroupRepository.findByMemberUserId(principal.id());
        return groups.stream().map(group -> toView(group, principal)).toList();
    }

    public RoleGroupView getGroup(Long groupId, AuthenticatedUser principal) {
        RoleGroup group = requireGroup(groupId);
        authorizationService.requireRoleManager(principal, groupId);
        return toView(group, principal);
    }

    @Transactional
    public RoleGroupView createGroup(String groupName, String remark, AuthenticatedUser principal) {
        authorizationService.requireDelegated(principal);
        requireSessionUser(principal);
        String normalizedName = normalizeRequired(groupName, "ROLE_GROUP_NAME_REQUIRED", "角色组名称不能为空");
        validateLength(normalizedName, MAX_GROUP_NAME_LENGTH, "ROLE_GROUP_NAME_TOO_LONG", "角色组名称不能超过128个字符");
        String normalizedRemark = normalizeOptional(remark);
        validateLength(normalizedRemark, MAX_REMARK_LENGTH, "ROLE_GROUP_REMARK_TOO_LONG", "备注不能超过256个字符");
        String operator = principal.userId();
        LocalDateTime now = LocalDateTime.now();
        RoleGroup saved = roleGroupRepository.save(RoleGroup.builder()
            .groupName(normalizedName)
            .remark(normalizedRemark)
            .status(1)
            .creator(operator)
            .modifier(operator)
            .gmtCreate(now)
            .gmtModified(now)
            .build());
        roleGroupRepository.saveMember(
            new RoleGroupMember(saved.getId(), principal.id(), null, RoleGroupMemberRole.OWNER),
            operator
        );
        audit(operator, "ROLE_GROUP_CREATE", "ROLE_GROUP", saved.getId(), saved.getGroupName());
        return toView(saved, principal);
    }

    @Transactional
    public RoleGroupView updateGroup(
        Long groupId,
        String groupName,
        String remark,
        AuthenticatedUser principal
    ) {
        RoleGroup existing = requireGroup(groupId);
        authorizationService.requireOwner(principal, groupId);
        String normalizedName = normalizeRequired(groupName, "ROLE_GROUP_NAME_REQUIRED", "角色组名称不能为空");
        validateLength(normalizedName, MAX_GROUP_NAME_LENGTH, "ROLE_GROUP_NAME_TOO_LONG", "角色组名称不能超过128个字符");
        String normalizedRemark = normalizeOptional(remark);
        validateLength(normalizedRemark, MAX_REMARK_LENGTH, "ROLE_GROUP_REMARK_TOO_LONG", "备注不能超过256个字符");
        RoleGroup saved = roleGroupRepository.save(existing.toBuilder()
            .groupName(normalizedName)
            .remark(normalizedRemark)
            .modifier(principal.userId())
            .gmtModified(LocalDateTime.now())
            .build());
        audit(principal.userId(), "ROLE_GROUP_UPDATE", "ROLE_GROUP", groupId, saved.getGroupName());
        return toView(saved, principal);
    }

    @Transactional
    public void deleteGroup(Long groupId, AuthenticatedUser principal) {
        RoleGroup group = requireGroup(groupId);
        authorizationService.requireOwner(principal, groupId);
        if (!roleRepository.findByGroupId(groupId).isEmpty()) {
            throw new BizException("ROLE_GROUP_NOT_EMPTY", "角色组仍包含角色，不能删除");
        }
        if (tokenRepository.countBySubject(PersonalAccessTokenSubjectType.ROLE_GROUP, groupId) > 0) {
            throw new BizException("ROLE_GROUP_TOKEN_EXISTS", "角色组仍包含访问令牌，不能删除");
        }
        roleGroupRepository.delete(groupId);
        audit(principal.userId(), "ROLE_GROUP_DELETE", "ROLE_GROUP", groupId, group.getGroupName());
    }

    public List<RoleGroupMember> listCollaborators(Long groupId, AuthenticatedUser principal) {
        requireGroup(groupId);
        authorizationService.requireRoleManager(principal, groupId);
        return roleGroupRepository.findMembers(groupId);
    }

    @Transactional
    public RoleGroupMember saveCollaborator(
        Long groupId,
        Long userId,
        RoleGroupMemberRole memberRole,
        AuthenticatedUser principal
    ) {
        requireGroup(groupId);
        authorizationService.requireOwner(principal, groupId);
        if (memberRole == null) {
            throw new BizException("ROLE_GROUP_MEMBER_ROLE_REQUIRED", "成员身份不能为空");
        }
        roleGroupRepository.lockById(groupId);
        PublicUser user = requirePublicUser(userId);
        RoleGroupMember existing = roleGroupRepository.findMember(groupId, userId).orElse(null);
        if (existing != null
            && existing.memberRole() == RoleGroupMemberRole.OWNER
            && memberRole != RoleGroupMemberRole.OWNER
            && roleGroupRepository.countOwners(groupId) <= 1) {
            throw new BizException("ROLE_GROUP_OWNER_REQUIRED", "角色组至少需要保留一名所有者");
        }
        RoleGroupMember member = new RoleGroupMember(groupId, userId, user.realName(), memberRole);
        roleGroupRepository.saveMember(member, principal.userId());
        audit(principal.userId(), "ROLE_GROUP_MEMBER_SAVE", "ROLE_GROUP", groupId,
            userId + ":" + memberRole.name());
        return member;
    }

    @Transactional
    public void removeCollaborator(Long groupId, Long userId, AuthenticatedUser principal) {
        requireGroup(groupId);
        authorizationService.requireOwner(principal, groupId);
        roleGroupRepository.lockById(groupId);
        RoleGroupMember member = roleGroupRepository.findMember(groupId, userId)
            .orElseThrow(() -> new BizException("ROLE_GROUP_MEMBER_NOT_FOUND", "角色组成员不存在"));
        if (member.memberRole() == RoleGroupMemberRole.OWNER && roleGroupRepository.countOwners(groupId) <= 1) {
            throw new BizException("ROLE_GROUP_OWNER_REQUIRED", "角色组至少需要保留一名所有者");
        }
        roleGroupRepository.removeMember(groupId, userId);
        audit(principal.userId(), "ROLE_GROUP_MEMBER_REMOVE", "ROLE_GROUP", groupId, String.valueOf(userId));
    }

    public List<PublicUser> searchPublicUsers(Long groupId, String keyword, AuthenticatedUser principal) {
        requireGroup(groupId);
        authorizationService.requireRoleManager(principal, groupId);
        String normalized = normalizeOptional(keyword);
        if (normalized == null || normalized.length() < 1) {
            return List.of();
        }
        return userRepository.searchPublicUsers(normalized, PUBLIC_USER_SEARCH_LIMIT);
    }

    public List<RoleGroupRoleView> listRoles(Long groupId, AuthenticatedUser principal) {
        requireGroup(groupId);
        authorizationService.requireRoleManager(principal, groupId);
        List<RoleGroupRoleView> result = new ArrayList<>();
        roleRepository.findByScope(RoleScope.GLOBAL).stream()
            .filter(role -> isAssignableRole(role, principal))
            .map(role -> toRoleView(role, false))
            .forEach(result::add);
        roleRepository.findByGroupId(groupId).stream()
            .map(role -> toRoleView(role, true))
            .forEach(result::add);
        return List.copyOf(result);
    }

    @Transactional
    public RoleGroupRoleView createRole(
        Long groupId,
        String roleCode,
        String roleName,
        String remark,
        AuthenticatedUser principal
    ) {
        requireGroup(groupId);
        authorizationService.requireRoleManager(principal, groupId);
        String normalizedCode = normalizeRoleCode(roleCode);
        roleRepository.findByCode(normalizedCode).ifPresent(existing -> {
            throw new BizException("ROLE_CODE_DUPLICATE", "角色编码已存在");
        });
        String normalizedName = normalizeRequired(roleName, "ROLE_NAME_REQUIRED", "角色名称不能为空");
        validateLength(normalizedName, 64, "ROLE_NAME_TOO_LONG", "角色名称不能超过64个字符");
        String normalizedRemark = normalizeOptional(remark);
        validateLength(normalizedRemark, MAX_REMARK_LENGTH, "ROLE_REMARK_TOO_LONG", "备注不能超过256个字符");
        Role saved = roleRepository.save(Role.builder()
            .roleCode(normalizedCode)
            .roleName(normalizedName)
            .permissionLevel(GROUP_ROLE_PERMISSION_LEVEL)
            .builtIn(0)
            .status(1)
            .remark(normalizedRemark)
            .roleScope(RoleScope.GROUP)
            .roleGroupId(groupId)
            .build());
        audit(principal.userId(), "ROLE_GROUP_ROLE_CREATE", "ROLE", saved.getId(), saved.getRoleCode());
        return toRoleView(saved, true);
    }

    @Transactional
    public RoleGroupRoleView updateRole(
        Long groupId,
        Long roleId,
        String roleName,
        String remark,
        AuthenticatedUser principal
    ) {
        authorizationService.requireRoleManager(principal, groupId);
        Role role = requireOwnedGroupRole(groupId, roleId);
        String normalizedName = normalizeRequired(roleName, "ROLE_NAME_REQUIRED", "角色名称不能为空");
        validateLength(normalizedName, 64, "ROLE_NAME_TOO_LONG", "角色名称不能超过64个字符");
        String normalizedRemark = normalizeOptional(remark);
        validateLength(normalizedRemark, MAX_REMARK_LENGTH, "ROLE_REMARK_TOO_LONG", "备注不能超过256个字符");
        Role saved = roleRepository.save(Role.builder()
            .id(role.getId())
            .roleCode(role.getRoleCode())
            .roleName(normalizedName)
            .permissionLevel(role.getPermissionLevel())
            .builtIn(role.getBuiltIn())
            .status(role.getStatus())
            .remark(normalizedRemark)
            .roleScope(RoleScope.GROUP)
            .roleGroupId(groupId)
            .build());
        audit(principal.userId(), "ROLE_GROUP_ROLE_UPDATE", "ROLE", roleId, saved.getRoleCode());
        return toRoleView(saved, true);
    }

    @Transactional
    public void deleteRole(Long groupId, Long roleId, AuthenticatedUser principal) {
        authorizationService.requireRoleManager(principal, groupId);
        Role role = requireOwnedGroupRole(groupId, roleId);
        if (roleRepository.existsUserBinding(roleId)) {
            throw new BizException("ROLE_IN_USE", "当前角色仍绑定用户，不能删除");
        }
        roleRepository.delete(roleId);
        policyRefreshService.refresh();
        audit(principal.userId(), "ROLE_GROUP_ROLE_DELETE", "ROLE", roleId, role.getRoleCode());
    }

    public List<PublicUser> listRoleMembers(Long groupId, Long roleId, AuthenticatedUser principal) {
        requireManageableRole(groupId, roleId, principal);
        return userRepository.findPublicUsersByRoleId(roleId);
    }

    @Transactional
    public void addRoleMembers(
        Long groupId,
        Long roleId,
        List<Long> userIds,
        AuthenticatedUser principal
    ) {
        Role role = requireManageableRole(groupId, roleId, principal);
        LinkedHashSet<Long> uniqueUserIds = userIds == null
            ? new LinkedHashSet<>()
            : userIds.stream().filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (uniqueUserIds.isEmpty()) {
            throw new BizException("ROLE_MEMBER_REQUIRED", "请选择至少一名用户");
        }
        for (Long userId : uniqueUserIds) {
            requirePublicUser(userId);
            userRepository.addRole(userId, roleId, principal.userId());
        }
        policyRefreshService.refresh();
        audit(principal.userId(), "ROLE_GROUP_ROLE_MEMBER_ADD", "ROLE", roleId,
            "count=" + uniqueUserIds.size() + ",role=" + role.getRoleCode());
    }

    @Transactional
    public void removeRoleMember(
        Long groupId,
        Long roleId,
        Long userId,
        AuthenticatedUser principal
    ) {
        Role role = requireManageableRole(groupId, roleId, principal);
        requirePublicUser(userId);
        userRepository.removeRole(userId, roleId, principal.userId());
        policyRefreshService.refresh();
        audit(principal.userId(), "ROLE_GROUP_ROLE_MEMBER_REMOVE", "ROLE", roleId,
            "user=" + userId + ",role=" + role.getRoleCode());
    }

    private Role requireManageableRole(Long groupId, Long roleId, AuthenticatedUser principal) {
        requireGroup(groupId);
        authorizationService.requireRoleManager(principal, groupId);
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "角色不存在"));
        boolean visible = role.getRoleScope() == RoleScope.GLOBAL
            || (role.getRoleScope() == RoleScope.GROUP && groupId.equals(role.getRoleGroupId()));
        if (!visible) {
            throw new BizException("ROLE_GROUP_ROLE_FORBIDDEN", "当前角色不属于此角色组的可管理范围");
        }
        if (!isAssignableRole(role, principal)) {
            throw new BizException("ROLE_GROUP_ROLE_ASSIGN_FORBIDDEN", "委派者不能分配管理级角色");
        }
        return role;
    }

    private boolean isAssignableRole(Role role, AuthenticatedUser principal) {
        if (authorizationService.isPlatformAdmin(principal)) {
            return true;
        }
        return role.getStatus() != null
            && role.getStatus() == 1
            && (role.getPermissionLevel() == null || role.getPermissionLevel() > 2)
            && !PLATFORM_ADMIN_ROLES.contains(role.getRoleCode());
    }

    private Role requireOwnedGroupRole(Long groupId, Long roleId) {
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> new BizException("ROLE_NOT_FOUND", "角色不存在"));
        if (role.getRoleScope() != RoleScope.GROUP || !groupId.equals(role.getRoleGroupId())) {
            throw new BizException("ROLE_GROUP_ROLE_FORBIDDEN", "只能维护当前角色组拥有的角色");
        }
        return role;
    }

    private RoleGroup requireGroup(Long groupId) {
        return roleGroupRepository.findById(groupId)
            .orElseThrow(() -> new BizException("ROLE_GROUP_NOT_FOUND", "角色组不存在"));
    }

    private PublicUser requirePublicUser(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BizException("USER_NOT_FOUND", "用户不存在"));
        if (user.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw new BizException("USER_NOT_ACTIVE", "只能选择在职用户");
        }
        return new PublicUser(user.getId(), user.getRealName());
    }

    private RoleGroupView toView(RoleGroup group, AuthenticatedUser principal) {
        List<RoleGroupMember> members = roleGroupRepository.findMembers(group.getId());
        RoleGroupMemberRole memberRole = principal == null || principal.id() == null
            ? null
            : members.stream()
                .filter(member -> principal.id().equals(member.userId()))
                .map(RoleGroupMember::memberRole)
                .findFirst()
                .orElse(null);
        String creatorName = group.getCreator() == null
            ? null
            : userRepository.findByUserId(group.getCreator())
                .map(User::getRealName)
                .orElse(null);
        List<String> ownerNames = members.stream()
            .filter(member -> member.memberRole() == RoleGroupMemberRole.OWNER)
            .map(RoleGroupMember::realName)
            .filter(java.util.Objects::nonNull)
            .toList();
        return new RoleGroupView(
            group.getId(),
            group.getGroupName(),
            group.getRemark(),
            group.getStatus(),
            memberRole,
            creatorName,
            ownerNames,
            members.size(),
            roleRepository.findByGroupId(group.getId()).size(),
            group.getGmtCreate(),
            group.getGmtModified()
        );
    }

    private RoleGroupRoleView toRoleView(Role role, boolean editable) {
        return new RoleGroupRoleView(
            role.getId(), role.getRoleCode(), role.getRoleName(), role.getPermissionLevel(), role.getStatus(),
            role.getRemark(), role.getRoleScope(), role.getRoleGroupId(), editable
        );
    }

    private String normalizeRoleCode(String roleCode) {
        String normalized = normalizeRequired(roleCode, "ROLE_CODE_REQUIRED", "角色编码不能为空")
            .toUpperCase(Locale.ROOT);
        if (!ROLE_CODE_PATTERN.matcher(normalized).matches()) {
            throw new BizException("ROLE_CODE_INVALID", "角色编码必须以字母开头，且只能包含大写字母、数字和下划线");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String code, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new BizException(code, message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateLength(String value, int maxLength, String code, String message) {
        if (value != null && value.length() > maxLength) {
            throw new BizException(code, message);
        }
    }

    private void requireSessionUser(AuthenticatedUser principal) {
        if (principal == null || principal.id() == null || principal.userId() == null || principal.userId().isBlank()) {
            throw new BizException("AUTH_FORBIDDEN", "当前身份不能执行此操作");
        }
    }

    private void audit(String operator, String operationType, String bizType, Long bizId, String afterJson) {
        auditLogRepository.save(AuditLog.builder()
            .operator(operator)
            .operationType(operationType)
            .bizType(bizType)
            .bizId(String.valueOf(bizId))
            .afterJson(afterJson)
            .result("SUCCESS")
            .build());
    }
}
