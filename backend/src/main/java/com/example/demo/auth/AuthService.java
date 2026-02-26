package com.example.demo.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.demo.email.EmailNotificationService;
import com.example.demo.group.DepartmentGroupService;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Service
public class AuthService {

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final MemberRepository memberRepository;
    private final AuthTokenService authTokenService;
    private final AuthMemberAdminService authMemberAdminService;
    private final EmailNotificationService emailNotificationService;
    private final DepartmentGroupService departmentGroupService;

    public AuthService(
            MemberRepository memberRepository,
            AuthTokenService authTokenService,
            AuthMemberAdminService authMemberAdminService,
            EmailNotificationService emailNotificationService,
            DepartmentGroupService departmentGroupService
    ) {
        this.memberRepository = memberRepository;
        this.authTokenService = authTokenService;
        this.authMemberAdminService = authMemberAdminService;
        this.emailNotificationService = emailNotificationService;
        this.departmentGroupService = departmentGroupService;
    }

    @Transactional
    public AuthResult register(String employeeId, String name, String email, String password, Long groupId) {
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
        if (groupId != null) {
            departmentGroupService.addMember(saved, groupId, saved.getId());
        }
        emailNotificationService.enqueueUserRegistered(saved);
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

        authTokenService.revokeMemberTokens(member.getId());
        return createToken(member);
    }

    @Transactional
    public void logout(String token) {
        authTokenService.revokeTokenIfPresent(token);
    }

    @Transactional(readOnly = true)
    public Member requireMember(String authorizationHeader) {
        return authTokenService.requireMemberByAuthorizationHeader(authorizationHeader);
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
        return authMemberAdminService.getAllMembers();
    }

    @Transactional
    public void deleteMemberById(Long id) {
        authMemberAdminService.deleteMemberById(id);
    }

    @Transactional
    public Member updateMemberRole(Long id, MemberRole role) {
        return authMemberAdminService.updateMemberRole(id, role);
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
        authTokenService.cleanupExpiredTokens();
    }

    private AuthResult createToken(Member member) {
        String tokenValue = authTokenService.issueToken(member);
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

    public record AuthResult(String token, MemberProfile member) {
    }

    public record MemberProfile(Long id, String employeeId, String name, String email, String role,
                                LocalDateTime createdAt) {
    }
}
