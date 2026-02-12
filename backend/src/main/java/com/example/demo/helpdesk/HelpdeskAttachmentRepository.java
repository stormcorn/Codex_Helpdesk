package com.example.demo.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HelpdeskAttachmentRepository extends JpaRepository<HelpdeskAttachment, Long> {
    Optional<HelpdeskAttachment> findByIdAndTicketId(Long id, Long ticketId);
}
