package com.example.demo.notification;

import com.example.demo.auth.AuthService;
import com.example.demo.auth.Member;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthService authService;

    public NotificationController(NotificationService notificationService, AuthService authService) {
        this.notificationService = notificationService;
        this.authService = authService;
    }

    @GetMapping
    public NotificationListResponse list(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Member member = authService.requireMember(authorization);
        List<NotificationResponse> notifications = notificationService.listForMember(member).stream()
                .map(NotificationResponse::from)
                .toList();
        long unreadCount = notificationService.unreadCount(member);
        return new NotificationListResponse(notifications, unreadCount);
    }

    @PatchMapping("/{notificationId}/read")
    public Map<String, String> markRead(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long notificationId
    ) {
        Member member = authService.requireMember(authorization);
        notificationService.markRead(member, notificationId);
        return Map.of("message", "ok");
    }

    @PatchMapping("/read-all")
    public Map<String, String> markAllRead(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Member member = authService.requireMember(authorization);
        notificationService.markAllRead(member);
        return Map.of("message", "ok");
    }

    public record NotificationListResponse(List<NotificationResponse> notifications, long unreadCount) {
    }

    public record NotificationResponse(Long id, String type, String message, Long ticketId, boolean read,
                                       LocalDateTime createdAt) {
        static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getType().name(),
                    notification.getMessage(),
                    notification.getTicketId(),
                    notification.isRead(),
                    notification.getCreatedAt()
            );
        }
    }
}
