package com.example.demo.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthInitializer {

    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@12345";

    @Bean
    CommandLineRunner initAdmin(
            AuthService authService,
            @Value("${app.admin.employee-id:ADMIN001}") String adminEmployeeId,
            @Value("${app.admin.name:System Admin}") String adminName,
            @Value("${app.admin.email:admin@example.com}") String adminEmail,
            @Value("${app.admin.password:Admin@12345}") String adminPassword
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
        if (DEFAULT_ADMIN_PASSWORD.equals(trimmed)
                || "changeme".equalsIgnoreCase(trimmed)) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD is using an insecure default value");
        }
    }
}
