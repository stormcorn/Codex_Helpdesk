package com.example.demo.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HelpdeskTicketMessageRepository extends JpaRepository<HelpdeskTicketMessage, Long> {
}
