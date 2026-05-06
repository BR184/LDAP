package com.company.idm.interfaces.system;

import com.company.idm.application.mail.MailConfigApplicationService;
import com.company.idm.application.mail.MailConfigTestCommand;
import com.company.idm.application.mail.MailConfigTestResult;
import com.company.idm.application.mail.MailServerConfigDetail;
import com.company.idm.application.mail.SaveMailConfigCommand;
import com.company.idm.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供系统管理下的邮件配置入口。
 */
@RestController
@RequestMapping("/api/v1/system/mail-config")
@RequiredArgsConstructor
public class MailConfigController {

    private final MailConfigApplicationService mailConfigApplicationService;

    @GetMapping
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/system/mail-config', 'GET')")
    public ApiResponse<MailConfigResponse> current() {
        return ApiResponse.success(toResponse(mailConfigApplicationService.getCurrentConfig()));
    }

    @PutMapping
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/system/mail-config', 'PUT')")
    public ApiResponse<MailConfigResponse> save(
        @Valid @RequestBody SaveMailConfigRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        return ApiResponse.success(toResponse(mailConfigApplicationService.saveConfig(new SaveMailConfigCommand(
            request.sendMode(),
            request.secureMode(),
            request.host(),
            request.port(),
            request.fromAddress(),
            request.fromName(),
            request.authRequired(),
            request.username(),
            request.password(),
            request.enabled(),
            request.remark(),
            username
        ))));
    }

    @PostMapping("/test")
    @PreAuthorize("@casbinAccessService.check(authentication, '/api/v1/system/mail-config/test', 'POST')")
    public ApiResponse<MailConfigTestResponse> test(
        @Valid @RequestBody TestMailConfigRequest request,
        @AuthenticationPrincipal(expression = "username") String username
    ) {
        MailConfigTestResult result = mailConfigApplicationService.testConfig(new MailConfigTestCommand(
            request.sendMode(),
            request.secureMode(),
            request.host(),
            request.port(),
            request.fromAddress(),
            request.fromName(),
            request.authRequired(),
            request.username(),
            request.password(),
            request.testToAddress(),
            username
        ));
        return ApiResponse.success(new MailConfigTestResponse(result.success(), result.message()));
    }

    private MailConfigResponse toResponse(MailServerConfigDetail detail) {
        if (detail == null) {
            return null;
        }
        return new MailConfigResponse(
            detail.id(),
            detail.sendMode(),
            detail.secureMode(),
            detail.host(),
            detail.port(),
            detail.fromAddress(),
            detail.fromName(),
            detail.authRequired(),
            detail.username(),
            detail.passwordConfigured(),
            detail.enabled(),
            detail.remark(),
            detail.lastTestSuccess(),
            detail.lastTestAt(),
            detail.lastTestMessage()
        );
    }
}
