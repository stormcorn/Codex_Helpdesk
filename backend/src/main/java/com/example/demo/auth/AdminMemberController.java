package com.example.demo.auth;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final AuthService authService;

    public AdminMemberController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public List<AuthService.MemberProfile> list(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        authService.requireAdmin(authorization);
        return authService.getAllMembers().stream()
                .map(authService::toMemberProfile)
                .toList();
    }

    @DeleteMapping("/{memberId}")
    public Map<String, String> delete(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long memberId
    ) {
        authService.requireAdmin(authorization);
        authService.deleteMemberById(memberId);
        return Map.of("message", "Member deleted");
    }

    @PatchMapping("/{memberId}/role")
    public AuthService.MemberProfile updateRole(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long memberId,
            @RequestBody RoleUpdateRequest request
    ) {
        authService.requireAdmin(authorization);
        MemberRole role;
        try {
            role = MemberRole.valueOf(request.role().trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("Role must be USER or IT");
        }
        Member updated = authService.updateMemberRole(memberId, role);
        return authService.toMemberProfile(updated);
    }

    public record RoleUpdateRequest(String role) {
    }
}
