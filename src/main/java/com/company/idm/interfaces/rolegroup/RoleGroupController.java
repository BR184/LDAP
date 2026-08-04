package com.company.idm.interfaces.rolegroup;

import com.company.idm.application.rolegroup.RoleGroupApplicationService;
import com.company.idm.application.rolegroup.RoleGroupRoleView;
import com.company.idm.application.rolegroup.RoleGroupView;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.domain.rolegroup.RoleGroupMember;
import com.company.idm.domain.rolegroup.RoleGroupMemberRole;
import com.company.idm.domain.user.PublicUser;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

@RestController
@RequestMapping("/api/v1/role-groups")
@RequiredArgsConstructor
@PreAuthorize("@casbinAccessService.hasAny(authentication, 'ROLE_GROUP_MANAGE')")
public class RoleGroupController {

    private final RoleGroupApplicationService roleGroupApplicationService;

    @GetMapping
    public ApiResponse<List<RoleGroupResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(roleGroupApplicationService.listGroups(principal).stream()
            .map(this::toResponse)
            .toList());
    }

    @GetMapping("/{groupId}")
    public ApiResponse<RoleGroupResponse> detail(
        @PathVariable Long groupId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(toResponse(roleGroupApplicationService.getGroup(groupId, principal)));
    }

    @PostMapping
    public ApiResponse<RoleGroupResponse> create(
        @Valid @RequestBody SaveRoleGroupRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(toResponse(roleGroupApplicationService.createGroup(
            request.groupName(), request.remark(), principal
        )));
    }

    @PutMapping("/{groupId}")
    public ApiResponse<RoleGroupResponse> update(
        @PathVariable Long groupId,
        @Valid @RequestBody SaveRoleGroupRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(toResponse(roleGroupApplicationService.updateGroup(
            groupId, request.groupName(), request.remark(), principal
        )));
    }

    @DeleteMapping("/{groupId}")
    public ApiResponse<Void> delete(
        @PathVariable Long groupId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        roleGroupApplicationService.deleteGroup(groupId, principal);
        return ApiResponse.success();
    }

    @GetMapping("/{groupId}/collaborators")
    public ApiResponse<List<RoleGroupMemberResponse>> collaborators(
        @PathVariable Long groupId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(roleGroupApplicationService.listCollaborators(groupId, principal).stream()
            .map(this::toResponse)
            .toList());
    }

    @PutMapping("/{groupId}/collaborators/{userId}")
    public ApiResponse<RoleGroupMemberResponse> saveCollaborator(
        @PathVariable Long groupId,
        @PathVariable Long userId,
        @Valid @RequestBody SaveRoleGroupMemberRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(toResponse(roleGroupApplicationService.saveCollaborator(
            groupId, userId, request.memberRole(), principal
        )));
    }

    @DeleteMapping("/{groupId}/collaborators/{userId}")
    public ApiResponse<Void> removeCollaborator(
        @PathVariable Long groupId,
        @PathVariable Long userId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        roleGroupApplicationService.removeCollaborator(groupId, userId, principal);
        return ApiResponse.success();
    }

    @GetMapping("/{groupId}/users")
    public ApiResponse<List<PublicUserResponse>> searchUsers(
        @PathVariable Long groupId,
        @RequestParam(defaultValue = "") String keyword,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(roleGroupApplicationService.searchPublicUsers(groupId, keyword, principal).stream()
            .map(this::toResponse)
            .toList());
    }

    @GetMapping("/{groupId}/roles")
    public ApiResponse<List<RoleGroupRoleResponse>> roles(
        @PathVariable Long groupId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(roleGroupApplicationService.listRoles(groupId, principal).stream()
            .map(this::toResponse)
            .toList());
    }

    @PostMapping("/{groupId}/roles")
    public ApiResponse<RoleGroupRoleResponse> createRole(
        @PathVariable Long groupId,
        @Valid @RequestBody CreateGroupRoleRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(toResponse(roleGroupApplicationService.createRole(
            groupId, request.roleCode(), request.roleName(), request.remark(), principal
        )));
    }

    @PutMapping("/{groupId}/roles/{roleId}")
    public ApiResponse<RoleGroupRoleResponse> updateRole(
        @PathVariable Long groupId,
        @PathVariable Long roleId,
        @Valid @RequestBody UpdateGroupRoleRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(toResponse(roleGroupApplicationService.updateRole(
            groupId, roleId, request.roleName(), request.remark(), principal
        )));
    }

    @DeleteMapping("/{groupId}/roles/{roleId}")
    public ApiResponse<Void> deleteRole(
        @PathVariable Long groupId,
        @PathVariable Long roleId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        roleGroupApplicationService.deleteRole(groupId, roleId, principal);
        return ApiResponse.success();
    }

    @GetMapping("/{groupId}/roles/{roleId}/members")
    public ApiResponse<List<PublicUserResponse>> roleMembers(
        @PathVariable Long groupId,
        @PathVariable Long roleId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return ApiResponse.success(roleGroupApplicationService.listRoleMembers(groupId, roleId, principal).stream()
            .map(this::toResponse)
            .toList());
    }

    @PostMapping("/{groupId}/roles/{roleId}/members")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'ROLE_GROUP_USER_ASSIGN')")
    public ApiResponse<Void> addRoleMembers(
        @PathVariable Long groupId,
        @PathVariable Long roleId,
        @Valid @RequestBody AddRoleMembersRequest request,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        roleGroupApplicationService.addRoleMembers(groupId, roleId, request.userIds(), principal);
        return ApiResponse.success();
    }

    @DeleteMapping("/{groupId}/roles/{roleId}/members/{userId}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'ROLE_GROUP_USER_ASSIGN')")
    public ApiResponse<Void> removeRoleMember(
        @PathVariable Long groupId,
        @PathVariable Long roleId,
        @PathVariable Long userId,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        roleGroupApplicationService.removeRoleMember(groupId, roleId, userId, principal);
        return ApiResponse.success();
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

    public record SaveRoleGroupRequest(
        @NotBlank @Size(max = 128) String groupName,
        @Size(max = 256) String remark
    ) {
    }

    public record SaveRoleGroupMemberRequest(@NotNull RoleGroupMemberRole memberRole) {
    }

    public record CreateGroupRoleRequest(
        @NotBlank @Size(max = 64) String roleCode,
        @NotBlank @Size(max = 64) String roleName,
        @Size(max = 256) String remark
    ) {
    }

    public record UpdateGroupRoleRequest(
        @NotBlank @Size(max = 64) String roleName,
        @Size(max = 256) String remark
    ) {
    }

    public record AddRoleMembersRequest(@NotEmpty List<@NotNull Long> userIds) {
    }
}
