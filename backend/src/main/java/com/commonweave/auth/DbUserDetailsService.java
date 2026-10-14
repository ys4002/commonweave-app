package com.commonweave.auth;

import com.commonweave.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DbUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public DbUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmail(email.trim().toLowerCase())
                .map(u -> new AppUserDetails(u.getId(), u.getEmail(), u.getPasswordHash(), u.isAdmin(),
                        u.getSessionVersion()))
                .orElseThrow(() -> new UsernameNotFoundException("unknown user"));
    }
}
