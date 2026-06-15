package com.swp391.api.config;

import com.swp391.api.modules.user.entity.User;
import com.swp391.api.modules.user.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DefaultUsersSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DefaultUsersSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUser("System Admin", "admin@goldenspoon.vn", "admin123", "0900000000", "ADMIN");
        seedUser("Restaurant Manager", "manager@goldenspoon.vn", "manager123", "0900000001", "MANAGER");
        seedUser("Front Desk", "reception@goldenspoon.vn", "reception123", "0900000002", "RECEPTIONIST");
        seedUser("Service Staff", "waiter@goldenspoon.vn", "waiter123", "0900000003", "WAITER");
    }

    private void seedUser(String fullName, String email, String rawPassword, String phone, String role) {
        userRepository.findByUserEmail(email).ifPresentOrElse(existing -> {
            if (existing.getIsActive() == null) {
                existing.setIsActive(true);
                userRepository.save(existing);
            }
        }, () -> {
            User user = new User();
            user.setFullName(fullName);
            user.setUserEmail(email);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setPhone(phone);
            user.setRole(role);
            user.setIsActive(true);
            userRepository.save(user);
        });
    }
}
