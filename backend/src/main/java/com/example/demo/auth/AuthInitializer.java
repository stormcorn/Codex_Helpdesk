package com.example.demo.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthInitializer {

    @Bean
    CommandLineRunner initAdmin(
            AuthService authService,
            @Value("${app.admin.employee-id:ADMIN001}") String adminEmployeeId,
            @Value("${app.admin.name:System Admin}") String adminName,
            @Value("${app.admin.email:admin@example.com}") String adminEmail,
            @Value("${app.admin.password:}") String adminPassword
    ) {
        return args -> {
            validateAdminPassword(adminPassword);
            authService.ensureDefaultAdmin(
                    adminEmployeeId,
                    adminName,
                    adminEmail,
                    adminPassword
            );
        };
    }

    private void validateAdminPassword(String adminPassword) {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD must be configured");
        }
        String trimmed = adminPassword.trim();
        if ("changeme".equalsIgnoreCase(trimmed)
                || "password".equalsIgnoreCase(trimmed)
                || "admin".equalsIgnoreCase(trimmed)
                || "admin123".equalsIgnoreCase(trimmed)
                || "admin@12345".equalsIgnoreCase(trimmed)) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD is using a weak value");
        }
    }
}
