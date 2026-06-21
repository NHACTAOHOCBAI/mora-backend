package com.mora.backend.config;

import com.mora.backend.model.entity.Role;
import com.mora.backend.model.entity.User;
import com.mora.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${mora.admin.username:admin}")
    private String adminUsername;

    @Value("${mora.admin.password:admin123}")
    private String adminPassword;

    @Value("${mora.admin.email:admin@mora.com}")
    private String adminEmail;

    @Value("${mora.admin.fullName:Mora System Admin}")
    private String adminFullName;

    @Override
    public void run(String... args) throws Exception {
        userRepository.findByUsername(adminUsername).ifPresentOrElse(
            admin -> {
                if (admin.getRole() != Role.ROLE_ADMIN) {
                    admin.setRole(Role.ROLE_ADMIN);
                    userRepository.save(admin);
                    log.info("Successfully restored Admin role to user: {}", adminUsername);
                } else {
                    log.info("Admin user already has ROLE_ADMIN. Skipping restoration.");
                }
            },
            () -> {
                User admin = User.builder()
                        .username(adminUsername)
                        .password(passwordEncoder.encode(adminPassword))
                        .email(adminEmail)
                        .fullName(adminFullName)
                        .role(Role.ROLE_ADMIN)
                        .active(true)
                        .build();

                userRepository.save(admin);
                log.info("Successfully seeded default Admin user: {}", adminUsername);
            }
        );
    }
}
