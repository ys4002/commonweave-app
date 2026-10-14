package com.commonweave.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AppUserDetails implements UserDetails, CredentialsContainer {
    private final long id;
    private final String email;
    private String passwordHash;
    private final boolean admin;
    private final int sessionVersion;

    public AppUserDetails(long id, String email, String passwordHash, boolean admin, int sessionVersion) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.admin = admin;
        this.sessionVersion = sessionVersion;
    }

    public int sessionVersion() {
        return sessionVersion;
    }

    public boolean admin() {
        return admin;
    }

    public long id() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return admin ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
