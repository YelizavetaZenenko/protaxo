package com.example.protaxo.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InvitationMailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    public void sendInvitation(String toEmail, String fullName, String token) {
        String link = baseUrl + "/accept-invite?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Запрошення до ProTaxo ERP");
        message.setText("""
                Вітаємо, %s!

                Вас запросили до системи ProTaxo ERP. Щоб встановити пароль і активувати акаунт, перейдіть за посиланням:
                %s

                Посилання дійсне 24 години.
                """.formatted(fullName, link));

        mailSender.send(message);
    }
}
