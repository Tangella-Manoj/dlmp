package com.dlmp.user.config;

import com.dlmp.user.domain.entity.User;
import com.dlmp.user.domain.enums.UserRole;
import com.dlmp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the initial ROLE_ADMIN account from environment variables on startup.
 * Replaces the old V1 SQL seed whose password was committed to the repository.
 *
 * Set ADMIN_EMAIL and ADMIN_PASSWORD (e.g. in Render dashboard) — no-op when
 * unset or when the user already exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${dlmp.admin.email:${ADMIN_EMAIL:}}")
    private String adminEmail;

    @Value("${dlmp.admin.password:${ADMIN_PASSWORD:}}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("AdminSeeder: ADMIN_EMAIL/ADMIN_PASSWORD not set — skipping admin bootstrap");
            return;
        }
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("AdminSeeder: admin '{}' already exists — nothing to do", adminEmail);
            return;
        }
        User admin = User.builder()
                .firstName("System")
                .lastName("Admin")
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(UserRole.ROLE_ADMIN)
                .status("ACTIVE")
                .build();
        userRepository.save(admin);
        log.info("AdminSeeder: created ROLE_ADMIN account '{}'", adminEmail);
    }
}
