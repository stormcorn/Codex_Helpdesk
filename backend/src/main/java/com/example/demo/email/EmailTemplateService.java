package com.example.demo.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailTemplateService {

    private final String appUrl;

    public EmailTemplateService(@Value("${app.email.app-url:http://localhost:5173}") String appUrl) {
        this.appUrl = appUrl;
    }

    public EmailMessage renderMessage(String templateKey, Map<String, Object> payload, String to) {
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

    private String asString(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
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
