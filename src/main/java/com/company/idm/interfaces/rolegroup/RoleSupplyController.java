package com.company.idm.interfaces.rolegroup;

import com.company.idm.application.rolegroup.RoleSupplyApplicationService;
import com.company.idm.application.rolegroup.RoleSupplyChanges;
import com.company.idm.application.rolegroup.RoleSupplyRoleSnapshot;
import com.company.idm.application.rolegroup.RoleSupplySnapshot;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.domain.rbac.RoleScope;
import com.company.idm.domain.rolegroup.RoleMembershipChange;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/open/role-supply")
@RequiredArgsConstructor
@PreAuthorize("@credentialAccessService.isRoleSupplyToken(authentication)")
public class RoleSupplyController {

    private final RoleSupplyApplicationService applicationService;

    @GetMapping("/snapshot")
    public ApiResponse<RoleSupplySnapshotResponse> snapshot(@AuthenticationPrincipal AuthenticatedUser principal) {
        RoleSupplySnapshot snapshot = applicationService.snapshot(principal);
        return ApiResponse.success(new RoleSupplySnapshotResponse(
            snapshot.snapshotCursor(),
            snapshot.generatedAt(),
            snapshot.roles().stream().map(this::toResponse).toList()
        ));
    }

    @GetMapping("/changes")
    public ApiResponse<RoleSupplyChangesResponse> changes(
        @RequestParam(defaultValue = "0") long cursor,
        @RequestParam(defaultValue = "200") int limit,
        @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        RoleSupplyChanges result = applicationService.changes(principal, cursor, limit);
        return ApiResponse.success(new RoleSupplyChangesResponse(
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
            role.memberNames()
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
            change.changeType(),
            change.gmtCreate()
        );
    }

    public record RoleSupplySnapshotResponse(
        long snapshotCursor,
        LocalDateTime generatedAt,
        List<RoleSupplyRoleResponse> roles
    ) {
    }

    public record RoleSupplyRoleResponse(
        Long roleId,
        String roleCode,
        String roleName,
        RoleScope roleScope,
        Long roleGroupId,
        List<String> memberNames
    ) {
    }

    public record RoleSupplyChangesResponse(
        long nextCursor,
        boolean hasMore,
        List<RoleSupplyChangeResponse> changes
    ) {
    }

    public record RoleSupplyChangeResponse(
        Long cursor,
        Long roleId,
        String roleCode,
        String roleName,
        RoleScope roleScope,
        Long roleGroupId,
        String memberName,
        String changeType,
        LocalDateTime changedAt
    ) {
    }
}
