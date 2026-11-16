package com.mora.backend.security;

import com.mora.backend.model.entity.User;
import com.mora.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        return principal(userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found")));
    }

    public UserDetails loadById(String userId) {
        return principal(userRepository.findById(Long.valueOf(userId))
                .orElseThrow(() -> new UsernameNotFoundException("Account not found")));
    }

    private UserDetails principal(User user) {
        if (!user.isActive()) throw new UsernameNotFoundException("Account disabled");
        // Spring Security calls this property username; its value is our immutable account ID.
        return new AccountPrincipal(user);
    }
}
