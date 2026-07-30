package com.company.idm.application.user;

public record UpdateUserAccessCommand(Long userId, boolean accessAllowed, String operator) {
}
