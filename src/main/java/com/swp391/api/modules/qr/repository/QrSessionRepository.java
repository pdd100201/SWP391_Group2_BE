package com.swp391.api.modules.qr.repository;

import com.swp391.api.modules.qr.entity.QrSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QrSessionRepository extends JpaRepository<QrSession, Long> {

    Optional<QrSession> findBySessionToken(String sessionToken);

    Optional<QrSession> findBySessionTokenAndStatus(String sessionToken, String status);
}
