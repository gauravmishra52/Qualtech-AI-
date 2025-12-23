package com.qualtech_ai.service.impl;

import com.qualtech_ai.exception.CustomException;
import com.qualtech_ai.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        log.info("Attempting to send email from: {} to: {} with subject: {}", fromAddress, to, subject);
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromAddress != null ? fromAddress : "");
            helper.setTo(to != null ? to : "");
            helper.setSubject(subject != null ? subject : "");
            helper.setText(body != null ? body : "", true);

            mailSender.send(mimeMessage);
            log.info("Email sent successfully from: {} to: {}", fromAddress, to);
        } catch (Exception e) {
            log.error("Failed to send email from: {} to: {}. Error: {}", fromAddress, to, e.getMessage());
            throw new CustomException("Could not send email: " + e.getMessage());
        }
    }
}
