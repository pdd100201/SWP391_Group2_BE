package com.swp391.api.modules.user.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.swp391.api.modules.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserEmail(String email);
}
