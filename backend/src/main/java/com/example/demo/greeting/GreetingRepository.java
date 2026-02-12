package com.example.demo.greeting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GreetingRepository extends JpaRepository<Greeting, Long> {
    Optional<Greeting> findTopByOrderByIdAsc();
}
