package com.company.idm.interfaces.v2.ldap;

import com.company.idm.application.ldap.ThirdPartyLdapFrameworkDetail;
import com.company.idm.application.ldap.ThirdPartyLdapIntegrationApplicationService;
import com.company.idm.application.ldap.ThirdPartyLdapPrecheckCommand;
import com.company.idm.application.ldap.ThirdPartyLdapPrecheckReport;
import com.company.idm.application.ldap.ThirdPartyLdapTemplateDetail;
import com.company.idm.common.api.ApiResponseV2;
import com.company.idm.interfaces.ldap.ThirdPartyLdapPrecheckRequest;
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
 * V2 第三方 LDAP 通用接入框架接口。
 */
@RestController
@RequestMapping("/api/v2/ldap")
@RequiredArgsConstructor
public class ThirdPartyLdapV2Controller {

    private final ThirdPartyLdapIntegrationApplicationService thirdPartyLdapIntegrationApplicationService;

    @GetMapping("/framework")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'LDAP_FRAMEWORK_READ')")
    public ApiResponseV2<ThirdPartyLdapFrameworkDetail> framework() {
        return ApiResponseV2.ok(thirdPartyLdapIntegrationApplicationService.getFramework());
    }

    @GetMapping("/templates/{systemCode}")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'LDAP_TEMPLATE_READ')")
    public ApiResponseV2<ThirdPartyLdapTemplateDetail> template(@PathVariable String systemCode) {
        return ApiResponseV2.ok(thirdPartyLdapIntegrationApplicationService.getTemplate(systemCode));
    }

    @PostMapping("/precheck")
    @PreAuthorize("@casbinAccessService.hasAny(authentication, 'LDAP_PRECHECK_EXECUTE')")
    public ApiResponseV2<ThirdPartyLdapPrecheckReport> precheck(@Valid @RequestBody ThirdPartyLdapPrecheckRequest request) {
        return ApiResponseV2.ok(thirdPartyLdapIntegrationApplicationService.precheck(
            new ThirdPartyLdapPrecheckCommand(request.systemCode(), request.enabledUsername(), request.disabledUsername())
        ));
    }
}
