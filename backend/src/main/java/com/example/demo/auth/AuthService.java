package com.example.demo.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Service
public class AuthService {

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final MemberRepository memberRepository;
    private final AuthTokenRepository tokenRepository;

    @Value("${app.auth.token-hours:24}")
    private long tokenHours;

    public AuthService(MemberRepository memberRepository, AuthTokenRepository tokenRepository) {
        this.memberRepository = memberRepository;
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public AuthResult register(String employeeId, String name, String email, String password) {
        String normalizedEmployeeId = normalize(employeeId);
        String normalizedName = normalize(name);
        String normalizedEmail = normalize(email).toLowerCase();

        validateRequired(normalizedEmployeeId, "Employee ID is required");
        validateRequired(normalizedName, "Name is required");
        validateRequired(normalizedEmail, "Email is required");
        validateRequired(password, "Password is required");

        if (password.length() < 8) {
            throw new ResponseStatusException(BAD_REQUEST, "Password must be at least 8 characters");
        }

        if (memberRepository.existsByEmployeeId(normalizedEmployeeId)) {
            throw new ResponseStatusException(CONFLICT, "Employee ID already exists");
        }
        if (memberRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(CONFLICT, "Email already exists");
        }

        Member member = new Member(
                normalizedEmployeeId,
                normalizedName,
                normalizedEmail,
                PASSWORD_ENCODER.encode(password),
                MemberRole.USER
        );

        Member saved = memberRepository.save(member);
        return createToken(saved);
    }

    @Transactional
    public AuthResult login(String employeeId, String password) {
        String normalizedEmployeeId = normalize(employeeId);
        validateRequired(normalizedEmployeeId, "Employee ID is required");
        validateRequired(password, "Password is required");

        Member member = memberRepository.findByEmployeeId(normalizedEmployeeId)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid credentials"));

        if (!PASSWORD_ENCODER.matches(password, member.getPasswordHash())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid credentials");
        }

        tokenRepository.deleteByMemberId(member.getId());
        return createToken(member);
    }

    @Transactional
    public void logout(String token) {
        tokenRepository.findByToken(token).ifPresent(tokenRepository::delete);
    }

    @Transactional(readOnly = true)
    public Member requireMember(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        AuthToken authToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Unauthorized"));

        if (authToken.isExpired()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Token expired");
        }

        return authToken.getMember();
    }

    @Transactional(readOnly = true)
    public Member requireAdmin(String authorizationHeader) {
        Member member = requireMember(authorizationHeader);
        if (member.getRole() != MemberRole.ADMIN) {
            throw new ResponseStatusException(FORBIDDEN, "Admin only");
        }
        return member;
    }

    @Transactional(readOnly = true)
    public Member requireItOrAdmin(String authorizationHeader) {
        Member member = requireMember(authorizationHeader);
        if (member.getRole() != MemberRole.IT && member.getRole() != MemberRole.ADMIN) {
            throw new ResponseStatusException(FORBIDDEN, "IT or Admin only");
        }
        return member;
    }

    @Transactional(readOnly = true)
    public List<Member> getAllMembers() {
        return memberRepository.findAll();
    }

    @Transactional
    public void deleteMemberById(Long id) {
        Member target = memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Member not found"));

        if (target.getRole() == MemberRole.ADMIN) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot delete admin account");
        }

        tokenRepository.deleteByMemberId(target.getId());
        memberRepository.delete(target);
    }

    @Transactional
    public Member updateMemberRole(Long id, MemberRole role) {
        Member target = memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Member not found"));
        if (target.getRole() == MemberRole.ADMIN) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot modify admin role");
        }
        if (role == MemberRole.ADMIN) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot assign admin role");
        }
        target.setRole(role);
        return memberRepository.save(target);
    }

    @Transactional
    public void ensureDefaultAdmin(String employeeId, String name, String email, String password) {
        if (memberRepository.existsByEmployeeId(employeeId)) {
            return;
        }

        Member admin = new Member(
                employeeId,
                name,
                email,
                PASSWORD_ENCODER.encode(password),
                MemberRole.ADMIN
        );

        memberRepository.save(admin);
    }

    @Transactional
    public void cleanupExpiredTokens() {
        tokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    private AuthResult createToken(Member member) {
        cleanupExpiredTokens();
        String tokenValue = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        AuthToken authToken = new AuthToken(tokenValue, member, LocalDateTime.now().plusHours(tokenHours));
        tokenRepository.save(authToken);

        return new AuthResult(tokenValue, toMemberProfile(member));
    }

    public MemberProfile toMemberProfile(Member member) {
        return new MemberProfile(
                member.getId(),
                member.getEmployeeId(),
                member.getName(),
                member.getEmail(),
                member.getRole().name(),
                member.getCreatedAt()
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private void validateRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unauthorized");
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }

    public record AuthResult(String token, MemberProfile member) {
    }

    public record MemberProfile(Long id, String employeeId, String name, String email, String role,
                                LocalDateTime createdAt) {
    }
}
