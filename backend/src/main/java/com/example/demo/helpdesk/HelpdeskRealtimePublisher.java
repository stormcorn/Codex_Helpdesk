package com.example.demo.helpdesk;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class HelpdeskRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public HelpdeskRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishTicketCreated(Long ticketId, Long actorMemberId) {
        publish("TICKET_CREATED", ticketId, actorMemberId);
    }

    public void publishTicketReplied(Long ticketId, Long actorMemberId) {
        publish("TICKET_REPLIED", ticketId, actorMemberId);
    }

    public void publishTicketStatusChanged(Long ticketId, Long actorMemberId) {
        publish("TICKET_STATUS_CHANGED", ticketId, actorMemberId);
    }

    public void publishTicketDeleted(Long ticketId, Long actorMemberId) {
        publish("TICKET_DELETED", ticketId, actorMemberId);
    }

    public void publishTicketSupervisorApproved(Long ticketId, Long actorMemberId) {
        publish("TICKET_SUPERVISOR_APPROVED", ticketId, actorMemberId);
    }

    private void publish(String type, Long ticketId, Long actorMemberId) {
        messagingTemplate.convertAndSend("/topic/tickets", new TicketRealtimeEvent(
                type,
                ticketId,
                actorMemberId,
                OffsetDateTime.now().toString()
        ));
    }

    public record TicketRealtimeEvent(String type, Long ticketId, Long actorMemberId, String at) {
    }
}
