package com.example.demo.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AuthSchemaPatch {

    @Bean
    CommandLineRunner patchMemberRoleConstraint(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute("ALTER TABLE members DROP CONSTRAINT IF EXISTS members_role_check");
            jdbcTemplate.execute("ALTER TABLE members ADD CONSTRAINT members_role_check CHECK (role IN ('ADMIN','IT','USER'))");
        };
    }
}
