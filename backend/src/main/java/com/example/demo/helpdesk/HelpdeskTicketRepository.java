package com.example.demo.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface HelpdeskTicketRepository extends JpaRepository<HelpdeskTicket, Long> {
    @EntityGraph(attributePaths = {"attachments", "messages"})
    List<HelpdeskTicket> findTop20ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"attachments", "messages"})
    Optional<HelpdeskTicket> findWithDetailsById(Long id);
}
