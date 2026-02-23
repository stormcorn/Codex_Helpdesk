package com.example.demo.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthTokenService {

    private static final String TOKEN_HASH_ALGORITHM = "SHA-256";

    private final AuthTokenRepository tokenRepository;

    @Value("${app.auth.token-hours:24}")
    private long tokenHours;

    public AuthTokenService(AuthTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public String issueToken(Member member) {
        cleanupExpiredTokens();
        String tokenValue = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        AuthToken authToken = new AuthToken(hashToken(tokenValue), member, LocalDateTime.now().plusHours(tokenHours));
        tokenRepository.save(authToken);
        return tokenValue;
    }

    @Transactional
    public void revokeMemberTokens(Long memberId) {
        tokenRepository.deleteByMemberId(memberId);
    }

    @Transactional
    public void revokeTokenIfPresent(String rawToken) {
        findAuthToken(rawToken).ifPresent(tokenRepository::delete);
    }

    @Transactional(readOnly = true)
    public Member requireMemberByAuthorizationHeader(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        AuthToken authToken = findAuthToken(token)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Unauthorized"));
        if (authToken.isExpired()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Token expired");
        }
        return authToken.getMember();
    }

    @Transactional
    public void cleanupExpiredTokens() {
        tokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    public String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unauthorized");
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }

    private Optional<AuthToken> findAuthToken(String rawToken) {
        String hashed = hashToken(rawToken);
        Optional<AuthToken> hashedToken = tokenRepository.findByToken(hashed);
        if (hashedToken.isPresent()) {
            return hashedToken;
        }
        // Compatibility path for old plaintext tokens that may still exist.
        return tokenRepository.findByToken(rawToken);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance(TOKEN_HASH_ALGORITHM);
            byte[] hashedBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashedBytes.length * 2);
            for (byte hashedByte : hashedBytes) {
                sb.append(String.format("%02x", hashedByte));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Missing token hash algorithm", ex);
        }
    }
}
