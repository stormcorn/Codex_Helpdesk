package com.example.demo.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResult register(@RequestBody RegisterRequest request) {
        return authService.register(request.employeeId(), request.name(), request.email(), request.password());
    }

    @PostMapping("/login")
    public AuthService.AuthResult login(@RequestBody LoginRequest request) {
        return authService.login(request.employeeId(), request.password());
    }

    @GetMapping("/me")
    public AuthService.MemberProfile me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Member member = authService.requireMember(authorization);
        return authService.toMemberProfile(member);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        Member member = authService.requireMember(authorization);
        authService.logout(authorization.substring("Bearer ".length()).trim());
        return Map.of("message", "Logged out", "employeeId", member.getEmployeeId());
    }

    public record RegisterRequest(String employeeId, String name, String email, String password) {
    }

    public record LoginRequest(String employeeId, String password) {
    }
}
