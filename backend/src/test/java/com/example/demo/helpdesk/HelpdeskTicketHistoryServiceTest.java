package com.example.demo.helpdesk;

import com.example.demo.auth.Member;
import com.example.demo.auth.MemberRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelpdeskTicketHistoryServiceTest {

    private final HelpdeskTicketHistoryService historyService = new HelpdeskTicketHistoryService();

    @Test
    void appendStatusHistoryUsesSystemActorWhenActorMissing() {
        HelpdeskCategory category = new HelpdeskCategory("General");
        HelpdeskTicket ticket = new HelpdeskTicket(
                "User",
                "user@example.com",
                "Subject",
                "Description",
                1L,
                null,
                category,
                HelpdeskTicketPriority.GENERAL
        );

        historyService.appendStatusHistory(ticket, HelpdeskTicketStatus.OPEN, HelpdeskTicketStatus.PROCEEDING, null);

        assertThat(ticket.getStatusHistories()).hasSize(1);
        HelpdeskTicketStatusHistory history = ticket.getStatusHistories().iterator().next();
        assertThat(history.getChangedByEmployeeId()).isEqualTo("SYSTEM");
        assertThat(history.getChangedByRole()).isEqualTo("SYSTEM");
    }

    @Test
    void appendStatusHistoryUsesMemberActorWhenProvided() {
        HelpdeskCategory category = new HelpdeskCategory("General");
        HelpdeskTicket ticket = new HelpdeskTicket(
                "User",
                "user@example.com",
                "Subject",
                "Description",
                1L,
                null,
                category,
                HelpdeskTicketPriority.GENERAL
        );
        Member actor = new Member("EMP001", "Tester", "tester@example.com", "hash", MemberRole.IT);

        historyService.appendStatusHistory(ticket, HelpdeskTicketStatus.OPEN, HelpdeskTicketStatus.PROCEEDING, actor);

        HelpdeskTicketStatusHistory history = ticket.getStatusHistories().iterator().next();
        assertThat(history.getChangedByEmployeeId()).isEqualTo("EMP001");
        assertThat(history.getChangedByName()).isEqualTo("Tester");
        assertThat(history.getChangedByRole()).isEqualTo("IT");
    }
}
