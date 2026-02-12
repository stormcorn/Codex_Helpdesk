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
            @Value("${app.admin.password:Admin@12345}") String adminPassword
    ) {
        return args -> authService.ensureDefaultAdmin(
                adminEmployeeId,
                adminName,
                adminEmail,
                adminPassword
        );
    }
}
