package com.company.idm.interfaces.ldap;

import com.company.idm.application.ldap.ThirdPartyLdapFrameworkDetail;
import com.company.idm.application.ldap.ThirdPartyLdapIntegrationApplicationService;
import com.company.idm.application.ldap.ThirdPartyLdapPrecheckCommand;
import com.company.idm.application.ldap.ThirdPartyLdapPrecheckReport;
import com.company.idm.application.ldap.ThirdPartyLdapTemplateDetail;
import com.company.idm.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供第三方 LDAP 通用接入框架的模板与预检接口。
 */
@Deprecated
@RestController
@RequestMapping("/api/v1/ldap")
@RequiredArgsConstructor
public class ThirdPartyLdapController {

    private final ThirdPartyLdapIntegrationApplicationService thirdPartyLdapIntegrationApplicationService;

    @GetMapping("/framework")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'LDAP_FRAMEWORK_READ')")
    public ApiResponse<ThirdPartyLdapFrameworkDetail> framework() {
        return ApiResponse.success(thirdPartyLdapIntegrationApplicationService.getFramework());
    }

    @GetMapping("/templates/{systemCode}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'LDAP_TEMPLATE_READ')")
    public ApiResponse<ThirdPartyLdapTemplateDetail> template(@PathVariable String systemCode) {
        return ApiResponse.success(thirdPartyLdapIntegrationApplicationService.getTemplate(systemCode));
    }

    @PostMapping("/precheck")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'LDAP_PRECHECK_EXECUTE')")
    public ApiResponse<ThirdPartyLdapPrecheckReport> precheck(@Valid @RequestBody ThirdPartyLdapPrecheckRequest request) {
        return ApiResponse.success(thirdPartyLdapIntegrationApplicationService.precheck(
            new ThirdPartyLdapPrecheckCommand(request.systemCode(), request.enabledUsername(), request.disabledUsername())
        ));
    }
}
