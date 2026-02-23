package com.example.demo.email;

import com.example.demo.auth.Member;
import com.example.demo.helpdesk.HelpdeskTicket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EmailPayloadFactory {

    private final String appUrl;

    public EmailPayloadFactory(@Value("${app.email.app-url:http://localhost:5173}") String appUrl) {
        this.appUrl = appUrl;
    }

    public Map<String, Object> payloadForMember(Member member) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recipientName", member.getName());
        payload.put("actionLabel", "您的帳號已註冊成功");
        payload.put("ticketId", "-");
        payload.put("subject", "Welcome");
        payload.put("ticketUrl", appUrl);
        return payload;
    }

    public Map<String, Object> payloadForTicket(HelpdeskTicket ticket, Member recipient, String actionLabel) {
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
}
