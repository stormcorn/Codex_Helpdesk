package com.example.demo.greeting;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GreetingInitializer {

    @Bean
    CommandLineRunner seedGreeting(GreetingService greetingService) {
        return args -> greetingService.initDefaultMessage();
    }
}
