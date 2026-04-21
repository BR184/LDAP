package com.company.idm.test.support;

import com.company.idm.infrastructure.security.RestAccessDeniedHandler;
import com.company.idm.infrastructure.security.RestAuthenticationEntryPoint;
import com.company.idm.interfaces.exception.GlobalExceptionHandler;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({
    ControllerTestSecurityConfig.class,
    GlobalExceptionHandler.class,
    RestAuthenticationEntryPoint.class,
    RestAccessDeniedHandler.class
})
public @interface ControllerMvcSlice {
}
