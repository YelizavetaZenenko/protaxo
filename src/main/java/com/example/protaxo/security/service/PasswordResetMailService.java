package com.example.protaxo.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordResetMailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url}")
    private String baseUrl;

    public void sendPasswordReset(String toEmail, String fullName, String token) {
        String link = baseUrl + "/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Відновлення паролю ProTaxo ERP");
        message.setText("""
                Вітаємо, %s!

                Хтось (сподіваємось, ви) запросив відновлення паролю до ProTaxo ERP. Щоб встановити новий пароль, перейдіть за посиланням:
                %s

                Якщо ви не запитували відновлення паролю — просто проігноруйте цей лист, ваш пароль не зміниться.

                Посилання дійсне 1 годину.
                """.formatted(fullName, link));

        mailSender.send(message);
    }
}
