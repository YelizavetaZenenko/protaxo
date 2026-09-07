package com.example.protaxo.security.config;

import com.example.protaxo.security.entity.Role;
import com.example.protaxo.security.entity.User;
import com.example.protaxo.security.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seedIfMissing("admin@protaxo.local", "Адміністратор", "admin123", Role.ADMIN);
        seedIfMissing("master@protaxo.local", "Майстер Іваненко", "master123", Role.MASTER);
    }

    private void seedIfMissing(String email, String fullName, String rawPassword, Role role) {
        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }
        User user = User.builder()
                .email(email)
                .fullName(fullName)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .active(true)
                .build();
        userRepository.save(user);
        log.info("Seeded default user {} with role {}", email, role);
    }
}
