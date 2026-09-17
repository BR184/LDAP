package com.company.idm.interfaces.v2.rolegroup;

import com.company.idm.application.rolegroup.RoleSupplyApplicationService;
import com.company.idm.application.rolegroup.RoleSupplyChanges;
import com.company.idm.application.rolegroup.RoleSupplyRoleSnapshot;
import com.company.idm.application.rolegroup.RoleSupplySnapshot;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.domain.rolegroup.RoleMembershipChange;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import com.company.idm.interfaces.rolegroup.RoleSupplyController.RoleSupplyChangeResponse;
import com.company.idm.interfaces.rolegroup.RoleSupplyController.RoleSupplyChangesResponse;
import com.company.idm.interfaces.rolegroup.RoleSupplyController.RoleSupplyRoleResponse;
import com.company.idm.interfaces.rolegroup.RoleSupplyController.RoleSupplySnapshotResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 角色供应开放接口（面向第三方系统，使用角色供应令牌鉴权）。
 */
@RestController
@RequestMapping("/api/v2/open/role-supply")
@RequiredArgsConstructor
@PreAuthorize("@credentialAccessService.isRoleSupplyToken(authentication)")
public class RoleSupplyV2Controller {

    private final RoleSupplyApplicationService applicationService;

    @GetMapping("/snapshot")
    public ApiResponseV2<RoleSupplySnapshotResponse> snapshot(@AuthenticationPrincipal AuthenticatedUser principal) {
        RoleSupplySnapshot snapshot = applicationService.snapshot(principal);
        return ApiResponseV2.ok(new RoleSupplySnapshotResponse(
            snapshot.snapshotCursor(),
            snapshot.generatedAt(),
            snapshot.roles().stream().map(this::toResponse).toList()
        ));
    }

    @GetMapping("/changes")
    public ApiResponseV2<RoleSupplyChangesResponse> changes(
        @RequestParam(defaultValue = "0") long cursor,
        @RequestParam(defaultValue = "200") int limit,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        RoleSupplyChanges result = applicationService.changes(principal, cursor, limit);
        return ApiResponseV2.ok(new RoleSupplyChangesResponse(
            result.nextCursor(),
            result.hasMore(),
            result.changes().stream().map(this::toResponse).toList()
        ));
    }

    private RoleSupplyRoleResponse toResponse(RoleSupplyRoleSnapshot role) {
        return new RoleSupplyRoleResponse(
            role.roleId(),
            role.roleCode(),
            role.roleName(),
            role.roleScope(),
            role.roleGroupId(),
            role.memberNames(),
            role.memberUserIds()
        );
    }

    private RoleSupplyChangeResponse toResponse(RoleMembershipChange change) {
        return new RoleSupplyChangeResponse(
            change.id(),
            change.roleId(),
            change.roleCode(),
            change.roleName(),
            change.roleScope(),
            change.roleGroupId(),
            change.memberName(),
            change.memberUserId(),
            change.changeType(),
            change.gmtCreate()
        );
    }
}
