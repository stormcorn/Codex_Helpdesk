package com.example.demo.notification;

import com.example.demo.auth.Member;
import com.example.demo.auth.MemberRepository;
import com.example.demo.auth.MemberRole;
import com.example.demo.group.DepartmentGroupMember;
import com.example.demo.group.DepartmentGroupMemberRepository;
import com.example.demo.helpdesk.HelpdeskTicket;
import com.example.demo.helpdesk.HelpdeskTicketPriority;
import com.example.demo.helpdesk.HelpdeskTicketStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final DepartmentGroupMemberRepository groupMemberRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            MemberRepository memberRepository,
            DepartmentGroupMemberRepository groupMemberRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.memberRepository = memberRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    @Transactional(readOnly = true)
    public List<Notification> listForMember(Member member) {
        return notificationRepository.findTop50ByRecipientIdOrderByCreatedAtDesc(member.getId());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Member member) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(member.getId());
    }

    @Transactional
    public void markRead(Member member, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, member.getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Notification not found"));
        notification.markRead();
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead(Member member) {
        List<Notification> all = notificationRepository.findTop50ByRecipientIdOrderByCreatedAtDesc(member.getId());
        all.forEach(Notification::markRead);
        notificationRepository.saveAll(all);
    }

    @Transactional
    public void notifyTicketCreated(HelpdeskTicket ticket, Member creator) {
        Map<Long, Member> recipientMap = new LinkedHashMap<>();
        memberRepository.findByRoleIn(List.of(MemberRole.IT, MemberRole.ADMIN))
                .forEach(member -> recipientMap.put(member.getId(), member));

        if (ticket.getPriority() == HelpdeskTicketPriority.URGENT && ticket.getGroup() != null) {
            groupMemberRepository.findByGroup_IdAndSupervisorTrue(ticket.getGroup().getId())
                    .map(DepartmentGroupMember::getMember)
                    .ifPresent(supervisor -> recipientMap.put(supervisor.getId(), supervisor));
        }

        List<Member> recipients = new ArrayList<>(recipientMap.values());
        String message = ticket.getPriority() == HelpdeskTicketPriority.URGENT
                ? String.format("急件工單 #%d 待主管確認：%s", ticket.getId(), ticket.getSubject())
                : String.format("新工單 #%d：%s", ticket.getId(), ticket.getSubject());
        createBulk(recipients, NotificationType.TICKET_CREATED, message, ticket.getId(), Set.of(creator.getId()));
    }

    @Transactional
    public void notifyTicketReplied(HelpdeskTicket ticket, Member replier) {
        if (ticket.getCreatedByMemberId() == null) {
            return;
        }
        memberRepository.findById(ticket.getCreatedByMemberId()).ifPresent(owner -> {
            if (owner.getId().equals(replier.getId())) {
                return;
            }
            String message = String.format("工單 #%d 有新回覆：%s", ticket.getId(), ticket.getSubject());
            Notification notification = new Notification(owner, NotificationType.TICKET_REPLY, message, ticket.getId());
            notificationRepository.save(notification);
        });
    }

    @Transactional
    public void notifyTicketStatusChanged(HelpdeskTicket ticket, Member actor, HelpdeskTicketStatus status) {
        if (ticket.getCreatedByMemberId() == null) {
            return;
        }
        memberRepository.findById(ticket.getCreatedByMemberId()).ifPresent(owner -> {
            if (owner.getId().equals(actor.getId())) {
                return;
            }
            String message = String.format("工單 #%d 狀態更新為 %s", ticket.getId(), status.name());
            Notification notification = new Notification(owner, NotificationType.TICKET_STATUS, message, ticket.getId());
            notificationRepository.save(notification);
        });
    }

    private void createBulk(List<Member> recipients, NotificationType type, String message, Long ticketId, Set<Long> skipIds) {
        List<Notification> notifications = recipients.stream()
                .filter(member -> !skipIds.contains(member.getId()))
                .map(member -> new Notification(member, type, message, ticketId))
                .toList();
        notificationRepository.saveAll(notifications);
    }
}
