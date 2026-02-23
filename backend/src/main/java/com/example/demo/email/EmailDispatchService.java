package com.example.demo.email;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EmailDispatchService {

    private static final List<EmailJobStatus> DISPATCHABLE_STATUSES = List.of(EmailJobStatus.PENDING, EmailJobStatus.RETRYING);

    private final EmailNotificationJobRepository jobRepository;
    private final EmailDeliveryLogRepository deliveryLogRepository;
    private final EmailProvider emailProvider;
    private final ObjectMapper objectMapper;
    private final EmailTemplateService emailTemplateService;
    private final int workerBatchSize;

    public EmailDispatchService(
            EmailNotificationJobRepository jobRepository,
            EmailDeliveryLogRepository deliveryLogRepository,
            EmailProvider emailProvider,
            ObjectMapper objectMapper,
            EmailTemplateService emailTemplateService,
            @Value("${app.email.worker-batch-size:50}") int workerBatchSize
    ) {
        this.jobRepository = jobRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.emailProvider = emailProvider;
        this.objectMapper = objectMapper;
        this.emailTemplateService = emailTemplateService;
        this.workerBatchSize = workerBatchSize;
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
            EmailMessage message = emailTemplateService.renderMessage(job.getTemplateKey(), payload, job.getRecipientEmail());
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
}
