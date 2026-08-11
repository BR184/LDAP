package com.company.idm.interfaces.v2.system;

import com.company.idm.application.mail.MailConfigApplicationService;
import com.company.idm.application.mail.MailConfigTestCommand;
import com.company.idm.application.mail.MailConfigTestResult;
import com.company.idm.application.mail.MailServerConfigDetail;
import com.company.idm.application.mail.SaveMailConfigCommand;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.interfaces.system.MailConfigResponse;
import com.company.idm.interfaces.system.MailConfigTestResponse;
import com.company.idm.interfaces.system.SaveMailConfigRequest;
import com.company.idm.interfaces.system.TestMailConfigRequest;
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
 * V2 邮件配置接口。
 */
@RestController
@RequestMapping("/api/v2/system/mail-config")
@RequiredArgsConstructor
public class MailConfigV2Controller {

    private final MailConfigApplicationService mailConfigApplicationService;

    @GetMapping
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'MAIL_CONFIG_READ')")
    public ApiResponseV2<MailConfigResponse> current() {
        return ApiResponseV2.ok(toResponse(mailConfigApplicationService.getCurrentConfig()));
    }

    @PutMapping
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'MAIL_CONFIG_SAVE')")
    public ApiResponseV2<MailConfigResponse> save(
        @Valid @RequestBody SaveMailConfigRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
    ) {
        return ApiResponseV2.ok(toResponse(mailConfigApplicationService.saveConfig(new SaveMailConfigCommand(
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
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'MAIL_CONFIG_TEST')")
    public ApiResponseV2<MailConfigTestResponse> test(
        @Valid @RequestBody TestMailConfigRequest request,
        @AuthenticationPrincipal(expression = "userId") String username
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
        return ApiResponseV2.ok(new MailConfigTestResponse(result.success(), result.message()));
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
