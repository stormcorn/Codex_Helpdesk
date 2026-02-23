package com.example.demo.helpdesk;

import com.example.demo.auth.Member;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Service
public class HelpdeskTicketHistoryService {

    public List<HelpdeskTicketMessage> sortMessagesByCreatedAt(Collection<HelpdeskTicketMessage> messages) {
        return messages.stream()
                .sorted(Comparator.comparing(HelpdeskTicketMessage::getCreatedAt))
                .toList();
    }

    public List<HelpdeskTicketStatusHistory> sortStatusHistoriesByCreatedAt(Collection<HelpdeskTicketStatusHistory> histories) {
        return histories.stream()
                .sorted(Comparator.comparing(HelpdeskTicketStatusHistory::getCreatedAt))
                .toList();
    }

    public void appendStatusHistory(
            HelpdeskTicket ticket,
            HelpdeskTicketStatus fromStatus,
            HelpdeskTicketStatus toStatus,
            Member actor
    ) {
        String employeeId = actor != null ? actor.getEmployeeId() : "SYSTEM";
        String name = actor != null ? actor.getName() : "System";
        String role = actor != null ? actor.getRole().name() : "SYSTEM";
        Long memberId = actor != null ? actor.getId() : null;

        ticket.addStatusHistory(new HelpdeskTicketStatusHistory(
                ticket,
                fromStatus,
                toStatus,
                memberId,
                employeeId,
                name,
                role
        ));
    }
}
