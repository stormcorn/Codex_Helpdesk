package com.example.demo.email;

import com.example.demo.auth.Member;
import com.example.demo.auth.MemberRepository;
import com.example.demo.group.DepartmentGroupMemberRepository;
import com.example.demo.helpdesk.HelpdeskTicket;
import com.example.demo.helpdesk.HelpdeskTicketPriority;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class EmailNotificationService {

    private static final String TRACE_ID_MDC_KEY = "traceId";

    private final EmailNotificationJobRepository jobRepository;
    private final MemberRepository memberRepository;
    private final DepartmentGroupMemberRepository groupMemberRepository;
    private final EmailPayloadFactory payloadFactory;
    private final EmailDispatchService emailDispatchService;
    private final ObjectMapper objectMapper;

    public EmailNotificationService(
            EmailNotificationJobRepository jobRepository,
            MemberRepository memberRepository,
            DepartmentGroupMemberRepository groupMemberRepository,
            EmailPayloadFactory payloadFactory,
            EmailDispatchService emailDispatchService,
            ObjectMapper objectMapper
    ) {
        this.jobRepository = jobRepository;
        this.memberRepository = memberRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.payloadFactory = payloadFactory;
        this.emailDispatchService = emailDispatchService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueueUserRegistered(Member member) {
        enqueue(
                EmailEventType.USER_REGISTERED,
                member.getId(),
                member.getEmail(),
                "user_registered_v1",
                payloadFactory.payloadForMember(member),
                "user_registered:" + member.getId()
        );
    }

    @Transactional
    public void enqueueTicketCreated(HelpdeskTicket ticket, Member creator) {
        enqueue(
                EmailEventType.TICKET_CREATED,
                creator.getId(),
                creator.getEmail(),
                "ticket_created_v1",
                payloadFactory.payloadForTicket(ticket, creator, "工單已建立"),
                "ticket_created:" + ticket.getId() + ":" + creator.getId()
        );

        if (ticket.getPriority() != HelpdeskTicketPriority.URGENT || ticket.getGroup() == null) {
            return;
        }

        groupMemberRepository.findByGroup_IdAndSupervisorTrue(ticket.getGroup().getId())
                .map(m -> m.getMember())
                .filter(supervisor -> !Objects.equals(supervisor.getId(), creator.getId()))
                .ifPresent(supervisor -> enqueue(
                        EmailEventType.TICKET_URGENT_SUPERVISOR_REQUIRED,
                        supervisor.getId(),
                        supervisor.getEmail(),
                        "ticket_urgent_supervisor_required_v1",
                        payloadFactory.payloadForTicket(ticket, supervisor, "急件待主管確認"),
                        "ticket_urgent_supervisor_required:" + ticket.getId() + ":" + supervisor.getId()
                ));
    }

    @Transactional
    public void enqueueTicketReplied(HelpdeskTicket ticket, Member replier) {
        if (ticket.getCreatedByMemberId() == null) return;
        memberRepository.findById(ticket.getCreatedByMemberId())
                .filter(owner -> !Objects.equals(owner.getId(), replier.getId()))
                .ifPresent(owner -> enqueue(
                        EmailEventType.TICKET_REPLIED,
                        owner.getId(),
                        owner.getEmail(),
                        "ticket_replied_v1",
                        payloadFactory.payloadForTicket(ticket, owner, "工單有新回覆"),
                        null
                ));
    }

    @Transactional
    public void enqueueTicketClosed(HelpdeskTicket ticket, Member actor) {
        if (ticket.getCreatedByMemberId() == null) return;
        memberRepository.findById(ticket.getCreatedByMemberId())
                .filter(owner -> !Objects.equals(owner.getId(), actor.getId()))
                .ifPresent(owner -> enqueue(
                        EmailEventType.TICKET_CLOSED,
                        owner.getId(),
                        owner.getEmail(),
                        "ticket_closed_v1",
                        payloadFactory.payloadForTicket(ticket, owner, "工單已完成"),
                        null
                ));
    }

    @Transactional(readOnly = true)
    public List<EmailNotificationJob> findDispatchableJobs() {
        return emailDispatchService.findDispatchableJobs();
    }

    public void dispatchPendingJobs() {
        emailDispatchService.dispatchPendingJobs();
    }

    @Transactional
    public void dispatchSingleJob(Long jobId) {
        emailDispatchService.dispatchSingleJob(jobId);
    }

    private void enqueue(
            EmailEventType eventType,
            Long recipientMemberId,
            String recipientEmail,
            String templateKey,
            Map<String, Object> payload,
            String dedupeKey
    ) {
        if (recipientEmail == null || recipientEmail.isBlank()) return;

        String traceId = normalize(MDC.get(TRACE_ID_MDC_KEY));
        String payloadJson = writeJson(payload);
        EmailNotificationJob job = new EmailNotificationJob(
                eventType,
                recipientMemberId,
                recipientEmail.trim().toLowerCase(),
                templateKey,
                "zh-TW",
                payloadJson,
                dedupeKey,
                traceId
        );
        try {
            jobRepository.save(job);
        } catch (DataIntegrityViolationException ignored) {
            // Dedupe key already exists, skip duplicate enqueue.
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize email payload", e);
        }
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
