package com.example.demo.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface HelpdeskTicketRepository extends JpaRepository<HelpdeskTicket, Long> {
    boolean existsByCategory_Id(Long categoryId);

    @EntityGraph(attributePaths = {"attachments", "messages", "statusHistories", "group", "category"})
    List<HelpdeskTicket> findTop20ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"attachments", "messages", "statusHistories", "group", "category"})
    Optional<HelpdeskTicket> findWithDetailsById(Long id);
}
