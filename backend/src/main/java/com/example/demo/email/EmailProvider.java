package com.example.demo.email;

public interface EmailProvider {
    String providerName();
    String send(EmailMessage message);
}
