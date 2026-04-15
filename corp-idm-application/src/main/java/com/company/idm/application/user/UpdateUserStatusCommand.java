package com.company.idm.application.user;

public record UpdateUserStatusCommand(Long userId, Integer statusCode, String operator) {
}

