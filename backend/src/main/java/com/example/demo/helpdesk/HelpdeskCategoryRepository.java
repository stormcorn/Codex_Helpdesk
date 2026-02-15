package com.example.demo.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HelpdeskCategoryRepository extends JpaRepository<HelpdeskCategory, Long> {
    boolean existsByNameIgnoreCase(String name);

    List<HelpdeskCategory> findAllByOrderByNameAsc();
}
