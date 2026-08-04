package com.company.idm.interfaces.rolegroup;

import com.company.idm.application.rolegroup.PersonalRoleContext;
import com.company.idm.application.rolegroup.PersonalRoleContextApplicationService;
import com.company.idm.common.api.ApiResponse;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/role-context")
@RequiredArgsConstructor
@PreAuthorize("@credentialAccessService.isSession(authentication)")
public class PersonalRoleContextController {

    private final PersonalRoleContextApplicationService applicationService;

    @GetMapping
    public ApiResponse<PersonalRoleContext> get(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success(applicationService.get(principal));
    }
}
