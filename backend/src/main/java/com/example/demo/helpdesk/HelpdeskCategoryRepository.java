package com.example.demo.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HelpdeskCategoryRepository extends JpaRepository<HelpdeskCategory, Long> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<HelpdeskCategory> findAllByOrderByNameAsc();
}
