package com.example.demo.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByToken(String token);

    void deleteByMemberId(Long memberId);

    void deleteByExpiresAtBefore(LocalDateTime time);
}
