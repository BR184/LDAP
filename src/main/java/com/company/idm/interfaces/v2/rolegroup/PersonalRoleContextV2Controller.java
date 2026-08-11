package com.company.idm.interfaces.v2.rolegroup;

import com.company.idm.application.rolegroup.PersonalRoleContext;
import com.company.idm.application.rolegroup.PersonalRoleContextApplicationService;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.infrastructure.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * V2 个人角色上下文接口。
 */
@RestController
@RequestMapping("/api/v2/users/me/role-context")
@RequiredArgsConstructor
@PreAuthorize("@credentialAccessService.isSession(authentication)")
public class PersonalRoleContextV2Controller {

    private final PersonalRoleContextApplicationService applicationService;

    @GetMapping
    public ApiResponseV2<PersonalRoleContext> get(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponseV2.ok(applicationService.get(principal));
    }
}
