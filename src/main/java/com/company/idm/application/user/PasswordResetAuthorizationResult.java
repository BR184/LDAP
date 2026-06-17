package com.company.idm.application.user;

public record PasswordResetAuthorizationResult(boolean allowed, PasswordResetScope scope) {

    public static PasswordResetAuthorizationResult denied() {
        return new PasswordResetAuthorizationResult(false, null);
    }

    public static PasswordResetAuthorizationResult allowed(PasswordResetScope scope) {
        return new PasswordResetAuthorizationResult(true, scope);
    }
}
