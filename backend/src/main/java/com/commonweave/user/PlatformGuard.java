package com.commonweave.user;

import com.commonweave.common.ApiException;
import org.springframework.stereotype.Component;

/** Platform admins administer the system; they never take part in circles, listings or handoffs. */
@Component
public class PlatformGuard {
    private final UserRepository users;

    public PlatformGuard(UserRepository users) {
        this.users = users;
    }

    public void forbidAdmins(Long userId) {
        if (users.findById(userId).map(User::isAdmin).orElse(false)) {
            throw ApiException.forbidden("Platform administrators cannot take part in circles or handoffs");
        }
    }
}
