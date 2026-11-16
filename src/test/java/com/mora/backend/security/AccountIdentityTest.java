package com.mora.backend.security;

import com.mora.backend.model.entity.Role;
import com.mora.backend.model.entity.User;
import com.mora.backend.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountIdentityTest {
    private static final String SECRET = "01234567890123456789012345678901";

    private JwtTokenProvider provider() {
        var provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secret", SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpiration", 60000L);
        provider.validateConfiguration();
        return provider;
    }

    private User account() {
        return User.builder().id(42L).email("person@example.com").fullName("Person")
                .password("encoded").role(Role.ROLE_USER).active(true).build();
    }

    @Test
    void emailLoginAndIdLookupProduceTheSamePrincipal() {
        var repository = mock(UserRepository.class);
        var account = account();
        when(repository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(repository.findById(42L)).thenReturn(Optional.of(account));
        var service = new CustomUserDetailsService(repository);
        var principal = service.loadUserByUsername(account.getEmail());
        assertEquals("42", principal.getUsername());
        assertEquals(principal, service.loadById("42"));
        var provider = provider();
        var token = provider.generateToken(principal);
        assertEquals("42", provider.extractUserId(token));
        assertTrue(provider.isTokenValid(token, principal));
        assertFalse(provider.isTokenValid(token,
                new org.springframework.security.core.userdetails.User("43", "encoded", principal.getAuthorities())));
    }

    @Test
    void rejectsLegacyTokensEvenWithNumericSubject() {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        for (String subject : new String[]{"old-handle", "42"}) {
            var legacy = Jwts.builder().subject(subject).expiration(new Date(System.currentTimeMillis() + 60000))
                    .signWith(key).compact();
            assertThrows(IllegalArgumentException.class, () -> provider().extractUserId(legacy));
        }
    }

    @Test
    void disabledAccountsCannotLoginOrReconnect() {
        var repository = mock(UserRepository.class);
        var account = account();
        account.setActive(false);
        when(repository.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        when(repository.findById(42L)).thenReturn(Optional.of(account));
        var service = new CustomUserDetailsService(repository);
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername(account.getEmail()));
        assertThrows(UsernameNotFoundException.class, () -> service.loadById("42"));
    }
}
