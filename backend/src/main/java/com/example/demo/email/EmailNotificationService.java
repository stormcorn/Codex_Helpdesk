package com.example.demo.email;

import com.example.demo.auth.Member;
import com.example.demo.auth.MemberRepository;
import com.example.demo.group.DepartmentGroupMemberRepository;
import com.example.demo.helpdesk.HelpdeskTicket;
import com.example.demo.helpdesk.HelpdeskTicketPriority;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class EmailNotificationService {

    private static final String TRACE_ID_MDC_KEY = "traceId";
    private static final List<EmailJobStatus> DISPATCHABLE_STATUSES = List.of(EmailJobStatus.PENDING, EmailJobStatus.RETRYING);

    private final EmailNotificationJobRepository jobRepository;
    private final EmailDeliveryLogRepository deliveryLogRepository;
    private final MemberRepository memberRepository;
    private final DepartmentGroupMemberRepository groupMemberRepository;
    private final EmailProvider emailProvider;
    private final ObjectMapper objectMapper;
    private final int workerBatchSize;
    private final String appUrl;

    public EmailNotificationService(
            EmailNotificationJobRepository jobRepository,
            EmailDeliveryLogRepository deliveryLogRepository,
            MemberRepository memberRepository,
            DepartmentGroupMemberRepository groupMemberRepository,
            EmailProvider emailProvider,
            ObjectMapper objectMapper,
            @Value("${app.email.worker-batch-size:50}") int workerBatchSize,
            @Value("${app.email.app-url:http://localhost:5173}") String appUrl
    ) {
        this.jobRepository = jobRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.memberRepository = memberRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.emailProvider = emailProvider;
        this.objectMapper = objectMapper;
        this.workerBatchSize = workerBatchSize;
        this.appUrl = appUrl;
    }

    @Transactional
    public void enqueueUserRegistered(Member member) {
        enqueue(
                EmailEventType.USER_REGISTERED,
                member.getId(),
                member.getEmail(),
                "user_registered_v1",
                payloadForMember(member),
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
                payloadForTicket(ticket, creator, "工單已建立"),
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
                        payloadForTicket(ticket, supervisor, "急件待主管確認"),
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
                        payloadForTicket(ticket, owner, "工單有新回覆"),
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
                        payloadForTicket(ticket, owner, "工單已完成"),
                        null
                ));
    }

    @Transactional(readOnly = true)
    public List<EmailNotificationJob> findDispatchableJobs() {
        List<EmailNotificationJob> jobs = jobRepository.findDispatchableJobs(DISPATCHABLE_STATUSES, LocalDateTime.now());
        if (jobs.size() <= workerBatchSize) return jobs;
        return jobs.subList(0, workerBatchSize);
    }

    public void dispatchPendingJobs() {
        List<EmailNotificationJob> jobs = findDispatchableJobs();
        for (EmailNotificationJob job : jobs) {
            dispatchSingleJob(job.getId());
        }
    }

    @Transactional
    public void dispatchSingleJob(Long jobId) {
        Optional<EmailNotificationJob> optional = jobRepository.findById(jobId);
        if (optional.isEmpty()) return;

        EmailNotificationJob job = optional.get();
        if (job.getStatus() != EmailJobStatus.PENDING && job.getStatus() != EmailJobStatus.RETRYING) {
            return;
        }

        job.markProcessing();
        jobRepository.save(job);

        try {
            Map<String, Object> payload = objectMapper.readValue(job.getPayloadJson(), new TypeReference<>() {
            });
            EmailMessage message = renderMessage(job.getTemplateKey(), payload, job.getRecipientEmail());
            String providerMessageId = emailProvider.send(message);
            job.markSent(providerMessageId);
            jobRepository.save(job);

            deliveryLogRepository.save(new EmailDeliveryLog(
                    job.getId(),
                    job.getEventType().name(),
                    job.getRecipientEmail(),
                    job.getTemplateKey(),
                    emailProvider.providerName(),
                    providerMessageId,
                    true,
                    null,
                    null,
                    job.getTraceId()
            ));
        } catch (Exception ex) {
            int nextAttempt = job.getAttempts() + 1;
            LocalDateTime nextRetryAt = LocalDateTime.now().plusMinutes(retryDelayMinutes(nextAttempt));
            job.markFailedAttempt(ex.getMessage(), nextRetryAt);
            jobRepository.save(job);

            deliveryLogRepository.save(new EmailDeliveryLog(
                    job.getId(),
                    job.getEventType().name(),
                    job.getRecipientEmail(),
                    job.getTemplateKey(),
                    emailProvider.providerName(),
                    null,
                    false,
                    "SEND_FAILED",
                    ex.getMessage(),
                    job.getTraceId()
            ));
        }
    }

    private long retryDelayMinutes(int attempt) {
        if (attempt <= 1) return 1;
        if (attempt == 2) return 5;
        if (attempt == 3) return 15;
        if (attempt == 4) return 60;
        return 360;
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

    private EmailMessage renderMessage(String templateKey, Map<String, Object> payload, String to) {
        String recipientName = asString(payload.get("recipientName"), "User");
        String ticketId = asString(payload.get("ticketId"), "-");
        String subject = asString(payload.get("subject"), "(no subject)");
        String ticketUrl = asString(payload.get("ticketUrl"), appUrl);
        String actionLabel = asString(payload.get("actionLabel"), "通知");

        String mailSubject = switch (templateKey) {
            case "user_registered_v1" -> "[Helpdesk] 註冊成功通知";
            case "ticket_created_v1" -> "[Helpdesk] 工單 #%s 已建立".formatted(ticketId);
            case "ticket_replied_v1" -> "[Helpdesk] 工單 #%s 有新回覆".formatted(ticketId);
            case "ticket_closed_v1" -> "[Helpdesk] 工單 #%s 已完成".formatted(ticketId);
            case "ticket_urgent_supervisor_required_v1" -> "[Helpdesk] 急件工單 #%s 待主管確認".formatted(ticketId);
            default -> "[Helpdesk] 通知";
        };

        String text = """
                %s 您好，

                %s
                工單編號：#%s
                主旨：%s
                連結：%s

                Helpdesk 系統通知
                """.formatted(recipientName, actionLabel, ticketId, subject, ticketUrl);
        String html = "<p>%s 您好，</p><p>%s</p><p>工單編號：<b>#%s</b><br/>主旨：%s<br/>連結：<a href=\"%s\">查看工單</a></p><p>Helpdesk 系統通知</p>"
                .formatted(escapeHtml(recipientName), escapeHtml(actionLabel), escapeHtml(ticketId), escapeHtml(subject), escapeHtml(ticketUrl));
        return new EmailMessage(to, mailSubject, html, text);
    }

    private Map<String, Object> payloadForMember(Member member) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientName", member.getName());
        payload.put("actionLabel", "您的帳號已註冊成功");
        payload.put("ticketId", "-");
        payload.put("subject", "Welcome");
        payload.put("ticketUrl", appUrl);
        return payload;
    }

    private Map<String, Object> payloadForTicket(HelpdeskTicket ticket, Member recipient, String actionLabel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientName", recipient.getName());
        payload.put("actionLabel", actionLabel);
        payload.put("ticketId", ticket.getId());
        payload.put("subject", ticket.getSubject());
        payload.put("status", ticket.getStatus().name());
        payload.put("priority", ticket.getPriority().name());
        payload.put("groupName", ticket.getGroup() == null ? null : ticket.getGroup().getName());
        payload.put("ticketUrl", appUrl + "/#ticket-" + ticket.getId());
        return payload;
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize email payload", e);
        }
    }

    private String asString(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String escapeHtml(String input) {
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
