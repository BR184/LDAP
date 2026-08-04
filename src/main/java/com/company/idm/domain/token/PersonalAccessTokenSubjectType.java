package com.company.idm.domain.token;

/**
 * Identifies the authority represented by a token without introducing a second credential system.
 */
public enum PersonalAccessTokenSubjectType {
    USER,
    ROLE_GROUP,
    GLOBAL
}
