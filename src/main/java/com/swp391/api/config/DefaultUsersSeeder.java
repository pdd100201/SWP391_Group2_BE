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
        seedUser("System Admin", "admin@goldenspoon.vn", "admin123", "0900000000", User.Role.ADMIN);
        seedUser("Restaurant Manager", "manager@goldenspoon.vn", "manager123", "0900000001", User.Role.MANAGER);
        seedUser("Front Desk", "reception@goldenspoon.vn", "reception123", "0900000002", User.Role.RECEPTIONIST);
        seedUser("Service Staff", "waiter@goldenspoon.vn", "waiter123", "0900000003", User.Role.WAITER);
    }

    private void seedUser(String fullName, String email, String rawPassword, String phone, User.Role role) {
        if (userRepository.findByUserEmail(email).isPresent()) {
            return;
        }

        User user = new User();
        user.setFullName(fullName);
        user.setUserEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setPhone(phone);
        user.setRole(role);
        user.setStatus(User.Status.ACTIVE);

        userRepository.save(user);
    }
}
