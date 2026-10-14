package com.commonweave.auth;

import com.commonweave.user.UserResponse;
import com.commonweave.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final UserService users;

    public AuthController(AuthService auth, UserService users) {
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody AuthDtos.SignupRequest req, HttpServletRequest http,
                               HttpServletResponse res) {
        return auth.signup(req, http, res);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody AuthDtos.LoginRequest req, HttpServletRequest http,
                              HttpServletResponse res) {
        return auth.login(req, http, res);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest http) {
        auth.logout(http);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return users.get(CurrentUser.id());
    }
}
