package com.commonweave.user;

import com.commonweave.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @PatchMapping("/me")
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest req) {
        return users.update(CurrentUser.id(), req);
    }
}
