package com.company.idm.application.auth;

import com.company.idm.domain.user.User;
import java.util.Set;

public interface TokenService {

    LoginResult generate(User user, Set<String> roleCodes);

    ParsedToken parse(String token);
}

