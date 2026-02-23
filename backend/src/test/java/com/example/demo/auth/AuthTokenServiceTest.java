package com.example.demo.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    @Mock
    private AuthTokenRepository tokenRepository;

    private AuthTokenService authTokenService;

    @BeforeEach
    void setUp() {
        authTokenService = new AuthTokenService(tokenRepository);
        ReflectionTestUtils.setField(authTokenService, "tokenHours", 24L);
    }

    @Test
    void issueTokenStoresHashedToken() {
        Member member = new Member("EMP001", "Tester", "tester@example.com", "hash", MemberRole.USER);

        String token = authTokenService.issueToken(member);

        assertThat(token).isNotBlank();
        verify(tokenRepository).save(any(AuthToken.class));
        verify(tokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
    }

    @Test
    void requireMemberByAuthorizationHeaderReturnsMemberWhenTokenExists() {
        Member member = new Member("EMP001", "Tester", "tester@example.com", "hash", MemberRole.USER);
        AuthToken authToken = new AuthToken("hashed-token", member, LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByToken(any(String.class))).thenReturn(Optional.of(authToken));

        Member resolved = authTokenService.requireMemberByAuthorizationHeader("Bearer raw-token");

        assertThat(resolved).isEqualTo(member);
    }
}
