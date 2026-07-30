package com.company.idm.domain.user;

import com.company.idm.common.enums.EmploymentStatus;
import org.springframework.stereotype.Component;

/**
 * Defines the single effective-login rule shared by platform and LDAP access.
 */
@Component
public class UserAccessPolicy {

    public boolean canAuthenticate(User user) {
        return user != null
            && user.isAccessAllowed()
            && user.getEmploymentStatus() == EmploymentStatus.ACTIVE;
    }
}
