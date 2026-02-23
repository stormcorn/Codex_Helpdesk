package com.example.demo.helpdesk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class HelpdeskAttachmentService {

    private static final long MAX_FILE_BYTES = 5L * 1024L * 1024L;

    private final HelpdeskAttachmentRepository attachmentRepository;
    private final Path uploadDir;

    public HelpdeskAttachmentService(
            HelpdeskAttachmentRepository attachmentRepository,
            @Value("${helpdesk.upload-dir:/tmp/helpdesk-uploads}") String uploadDir
    ) {
        this.attachmentRepository = attachmentRepository;
        this.uploadDir = Path.of(uploadDir);
    }

    @Transactional
    public void saveAttachments(HelpdeskTicket ticket, List<MultipartFile> files) {
        try {
            Files.createDirectories(uploadDir);
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }

                validateFileSize(file);
                String originalFilename = sanitizeFilename(file.getOriginalFilename());
                String storedFilename = UUID.randomUUID() + "-" + originalFilename;
                Path target = uploadDir.resolve(storedFilename);

                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
                }

                HelpdeskAttachment attachment = new HelpdeskAttachment(
                        ticket,
                        originalFilename,
                        storedFilename,
                        defaultContentType(file.getContentType()),
                        file.getSize()
                );
                ticket.addAttachment(attachment);
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("File upload failed");
        }
    }

    public HelpdeskTicketService.AttachmentDownload getAttachment(Long ticketId, Long attachmentId) {
        HelpdeskAttachment attachment = attachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Attachment not found"));
        Path path = uploadDir.resolve(attachment.getStoredFilename());
        if (!Files.exists(path)) {
            throw new ResponseStatusException(NOT_FOUND, "Attachment file missing");
        }
        return new HelpdeskTicketService.AttachmentDownload(path, attachment.getOriginalFilename(), attachment.getContentType());
    }

    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Each file must be smaller than 5MB");
        }
    }

    private String sanitizeFilename(String filename) {
        String cleaned = StringUtils.cleanPath(filename == null ? "file" : filename);
        return cleaned.replaceAll("[\\\\/]+", "_");
    }

    private String defaultContentType(String contentType) {
        return (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
    }
}
