package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.request.LoginRequest;
import com.mora.backend.model.dto.request.RegisterRequest;
import com.mora.backend.model.dto.response.AuthResponse;
import com.mora.backend.model.dto.response.UserResponse;
import com.mora.backend.model.entity.Role;
import com.mora.backend.model.entity.User;
import com.mora.backend.repository.UserRepository;
import com.mora.backend.security.JwtTokenProvider;
import com.mora.backend.core.LegalPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)

                .password("encoded_password")
                .email("testuser@example.com")
                .fullName("Test User")
                .role(Role.ROLE_USER)
                .active(true)
                .build();

        registerRequest = new RegisterRequest();
        registerRequest.setPassword("password123");
        registerRequest.setEmail("testuser@example.com");
        registerRequest.setFullName("Test User");
        registerRequest.setAcceptsTerms(true);
        registerRequest.setAcceptsPrivacy(true);

        loginRequest = new LoginRequest();
        loginRequest.setEmail("testuser@example.com");
        loginRequest.setPassword("password123");
    }

    @Test
    void register_Success() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("encoded_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.register(registerRequest);

        assertNotNull(response);
        assertEquals(user.getEmail(), response.getEmail());
        assertEquals(LegalPolicy.TERMS_VERSION, response.getTermsAcceptedVersion());
        assertEquals(LegalPolicy.PRIVACY_VERSION, response.getPrivacyAcceptedVersion());
        assertFalse(response.isLegalAcceptanceRequired());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_UserExisted() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

        AppException exception = assertThrows(AppException.class, () -> userService.register(registerRequest));
        assertEquals(ErrorCode.USER_EXISTED, exception.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Success() {
        Authentication authentication = mock(Authentication.class);
        UserDetails userDetails = mock(UserDetails.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(userDetails)).thenReturn("dummy_token");

        AuthResponse response = userService.login(loginRequest);

        assertNotNull(response);
        assertEquals("dummy_token", response.getToken());
        assertEquals("testuser@example.com", response.getEmail());
    }

    @Test
    void login_Failure() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new RuntimeException("Bad credentials"));

        AppException exception = assertThrows(AppException.class, () -> userService.login(loginRequest));
        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }
}
