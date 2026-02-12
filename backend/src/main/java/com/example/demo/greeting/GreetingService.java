package com.example.demo.greeting;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    private static final String DEFAULT_MESSAGE = "Hello from PostgreSQL + Spring Data JPA";

    private final GreetingRepository greetingRepository;

    public GreetingService(GreetingRepository greetingRepository) {
        this.greetingRepository = greetingRepository;
    }

    public String getMessage() {
        return greetingRepository.findTopByOrderByIdAsc()
                .map(Greeting::getMessage)
                .orElse(DEFAULT_MESSAGE);
    }

    public void initDefaultMessage() {
        if (greetingRepository.count() == 0) {
            greetingRepository.save(new Greeting(DEFAULT_MESSAGE));
        }
    }
}
