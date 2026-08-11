package com.company.idm.interfaces.v2.rolegroup;

import com.company.idm.application.rolegroup.RoleGroupApplicationService;
import com.company.idm.application.rolegroup.RoleGroupRoleView;
import com.company.idm.application.rolegroup.RoleGroupView;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.domain.rolegroup.RoleGroupMember;
import com.company.idm.domain.user.PublicUser;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.interfaces.rolegroup.PublicUserResponse;
import com.company.idm.interfaces.rolegroup.RoleGroupMemberResponse;
import com.company.idm.interfaces.rolegroup.RoleGroupResponse;
import com.company.idm.interfaces.rolegroup.RoleGroupRoleResponse;
import com.company.idm.interfaces.rolegroup.RoleGroupController.AddRoleMembersRequest;
import com.company.idm.interfaces.rolegroup.RoleGroupController.CreateGroupRoleRequest;
import com.company.idm.interfaces.rolegroup.RoleGroupController.SaveRoleGroupMemberRequest;
import com.company.idm.interfaces.rolegroup.RoleGroupController.SaveRoleGroupRequest;
import com.company.idm.interfaces.rolegroup.RoleGroupController.UpdateGroupRoleRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 角色组管理接口。
 */
@RestController
@RequestMapping("/api/v2/role-groups")
@RequiredArgsConstructor
public class RoleGroupV2Controller {

    private static final String READ_OR_MANAGE =
        "@casbinAccessService.hasAny(authentication, 'ROLE_GROUP_READ', 'ROLE_GROUP_MANAGE')";
    private static final String MANAGE =
        "@casbinAccessService.hasAny(authentication, 'ROLE_GROUP_MANAGE')";
    private static final String MANAGE_AND_ASSIGN =
        "@casbinAccessService.hasAny(authentication, 'ROLE_GROUP_MANAGE')"
            + " and @casbinAccessService.hasAny(authentication, 'ROLE_GROUP_USER_ASSIGN')";

    private final RoleGroupApplicationService roleGroupApplicationService;

    @GetMapping
    @PreAuthorize(READ_OR_MANAGE)
    public ApiResponseV2<List<RoleGroupResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponseV2.ok(roleGroupApplicationService.listGroups(principal).stream()
            .map(this::toResponse)
            .toList());
    }

    @GetMapping("/{groupId}")
    @PreAuthorize(READ_OR_MANAGE)
    public ApiResponseV2<RoleGroupResponse> detail(
        @PathVariable Long groupId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(toResponse(roleGroupApplicationService.getGroup(groupId, principal)));
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    public ApiResponseV2<RoleGroupResponse> create(
        @Valid @RequestBody SaveRoleGroupRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(toResponse(roleGroupApplicationService.createGroup(
            request.groupName(), request.remark(), principal
        )));
    }

    @PutMapping("/{groupId}")
    @PreAuthorize(MANAGE)
    public ApiResponseV2<RoleGroupResponse> update(
        @PathVariable Long groupId,
        @Valid @RequestBody SaveRoleGroupRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(toResponse(roleGroupApplicationService.updateGroup(
            groupId, request.groupName(), request.remark(), principal
        )));
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize(MANAGE)
    public ApiResponseV2<Void> delete(
        @PathVariable Long groupId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        roleGroupApplicationService.deleteGroup(groupId, principal);
        return ApiResponseV2.ok();
    }

    @GetMapping("/{groupId}/collaborators")
    @PreAuthorize(READ_OR_MANAGE)
    public ApiResponseV2<List<RoleGroupMemberResponse>> collaborators(
        @PathVariable Long groupId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(roleGroupApplicationService.listCollaborators(groupId, principal).stream()
            .map(this::toResponse)
            .toList());
    }

    @PutMapping("/{groupId}/collaborators/{userId}")
    @PreAuthorize(MANAGE)
    public ApiResponseV2<RoleGroupMemberResponse> saveCollaborator(
        @PathVariable Long groupId,
        @PathVariable Long userId,
        @Valid @RequestBody SaveRoleGroupMemberRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(toResponse(roleGroupApplicationService.saveCollaborator(
            groupId, userId, request.memberRole(), principal
        )));
    }

    @DeleteMapping("/{groupId}/collaborators/{userId}")
    @PreAuthorize(MANAGE)
    public ApiResponseV2<Void> removeCollaborator(
        @PathVariable Long groupId,
        @PathVariable Long userId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        roleGroupApplicationService.removeCollaborator(groupId, userId, principal);
        return ApiResponseV2.ok();
    }

    @GetMapping("/{groupId}/users")
    @PreAuthorize(READ_OR_MANAGE)
    public ApiResponseV2<List<PublicUserResponse>> searchUsers(
        @PathVariable Long groupId,
        @RequestParam(defaultValue = "") String keyword,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(roleGroupApplicationService.searchPublicUsers(groupId, keyword, principal).stream()
            .map(this::toResponse)
            .toList());
    }

    @GetMapping("/{groupId}/roles")
    @PreAuthorize(READ_OR_MANAGE)
    public ApiResponseV2<List<RoleGroupRoleResponse>> roles(
        @PathVariable Long groupId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(roleGroupApplicationService.listRoles(groupId, principal).stream()
            .map(this::toResponse)
            .toList());
    }

    @PostMapping("/{groupId}/roles")
    @PreAuthorize(MANAGE)
    public ApiResponseV2<RoleGroupRoleResponse> createRole(
        @PathVariable Long groupId,
        @Valid @RequestBody CreateGroupRoleRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(toResponse(roleGroupApplicationService.createRole(
            groupId, request.roleCode(), request.roleName(), request.remark(), principal
        )));
    }

    @PutMapping("/{groupId}/roles/{roleId}")
    @PreAuthorize(MANAGE)
    public ApiResponseV2<RoleGroupRoleResponse> updateRole(
        @PathVariable Long groupId,
        @PathVariable Long roleId,
        @Valid @RequestBody UpdateGroupRoleRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(toResponse(roleGroupApplicationService.updateRole(
            groupId, roleId, request.roleName(), request.remark(), principal
        )));
    }

    @DeleteMapping("/{groupId}/roles/{roleId}")
    @PreAuthorize(MANAGE)
    public ApiResponseV2<Void> deleteRole(
        @PathVariable Long groupId,
        @PathVariable Long roleId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        roleGroupApplicationService.deleteRole(groupId, roleId, principal);
        return ApiResponseV2.ok();
    }

    @GetMapping("/{groupId}/roles/{roleId}/members")
    @PreAuthorize(READ_OR_MANAGE)
    public ApiResponseV2<List<PublicUserResponse>> roleMembers(
        @PathVariable Long groupId,
        @PathVariable Long roleId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponseV2.ok(roleGroupApplicationService.listRoleMembers(groupId, roleId, principal).stream()
            .map(this::toResponse)
            .toList());
    }

    @PostMapping("/{groupId}/roles/{roleId}/members")
    @PreAuthorize(MANAGE_AND_ASSIGN)
    public ApiResponseV2<Void> addRoleMembers(
        @PathVariable Long groupId,
        @PathVariable Long roleId,
        @Valid @RequestBody AddRoleMembersRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        roleGroupApplicationService.addRoleMembers(groupId, roleId, request.userIds(), principal);
        return ApiResponseV2.ok();
    }

    @DeleteMapping("/{groupId}/roles/{roleId}/members/{userId}")
    @PreAuthorize(MANAGE_AND_ASSIGN)
    public ApiResponseV2<Void> removeRoleMember(
        @PathVariable Long groupId,
        @PathVariable Long roleId,
        @PathVariable Long userId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        roleGroupApplicationService.removeRoleMember(groupId, roleId, userId, principal);
        return ApiResponseV2.ok();
    }

    private RoleGroupResponse toResponse(RoleGroupView view) {
        return new RoleGroupResponse(
            view.id(), view.groupName(), view.remark(), view.status(), view.currentMemberRole(),
            view.creatorName(), view.ownerNames(),
            view.memberCount(), view.roleCount(), view.gmtCreate(), view.gmtModified()
        );
    }

    private RoleGroupMemberResponse toResponse(RoleGroupMember member) {
        return new RoleGroupMemberResponse(member.userId(), member.realName(), member.memberRole());
    }

    private RoleGroupRoleResponse toResponse(RoleGroupRoleView role) {
        return new RoleGroupRoleResponse(
            role.id(), role.roleCode(), role.roleName(), role.permissionLevel(), role.status(), role.remark(),
            role.roleScope(), role.roleGroupId(), role.editable()
        );
    }

    private PublicUserResponse toResponse(PublicUser user) {
        return new PublicUserResponse(user.id(), user.realName());
    }
}
