package com.mora.backend.security;

import com.mora.backend.model.entity.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

public class AccountPrincipal extends org.springframework.security.core.userdetails.User {
    private final long credentialVersion;

    public AccountPrincipal(User user) {
        super(user.getId().toString(), user.getPassword(), user.isActive(),
                true, true, true, List.of(new SimpleGrantedAuthority(user.getRole().name())));
        credentialVersion = user.getCredentialVersion();
    }

    public long getCredentialVersion() { return credentialVersion; }
}
