package com.copilot.tools.email;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.host:localhost}")
    private String mailHost;

    @Value("${spring.mail.port:1025}")
    private Integer mailPort;

    @Value("${spring.mail.username:assistant@company.com}")
    private String fromEmail;

    public String sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromEmail);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);

            log.info("Попытка отправки письма через SMTP {}:{}: from={}, to={}, subject={}", 
                    mailHost, mailPort, fromEmail, to, subject);
            
            mailSender.send(msg);
            
            log.info("Email sent successfully to: {} via SMTP {}:{}. From: {}, Subject: {}", 
                    to, mailHost, mailPort, fromEmail, subject);
            return "sent:" + System.currentTimeMillis();
        } catch (MailException e) {
            String errorMessage = buildErrorMessage(e, to);
            log.error("Failed to send email to {} via SMTP {}:{} - {}", 
                    to, mailHost, mailPort, errorMessage, e);
            throw new RuntimeException("Email send failed: " + errorMessage, e);
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    public String sendBulkEmails(String[] recipients, String subject, String body) {
        int successCount = 0;
        int failCount = 0;
        StringBuilder errors = new StringBuilder();

        for (String recipient : recipients) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromEmail);
                msg.setTo(recipient);
                msg.setSubject(subject);
                msg.setText(body);

                mailSender.send(msg);
                log.info("Email sent successfully to: {} via SMTP {}:{}", recipient, mailHost, mailPort);
                successCount++;
            } catch (MailException e) {
                String errorMessage = buildErrorMessage(e, recipient);
                log.error("Failed to send email to {} via SMTP {}:{} - {}", 
                        recipient, mailHost, mailPort, errorMessage, e);
                failCount++;
                errors.append(recipient).append(": ").append(errorMessage).append("; ");
            } catch (Exception e) {
                log.error("Unexpected error sending email to {}: {}", recipient, e.getMessage(), e);
                failCount++;
                errors.append(recipient).append(": ").append(e.getMessage()).append("; ");
            }
        }
        
        log.info("Bulk email sent: {} successful, {} failed out of {} recipients via SMTP {}:{}", 
                successCount, failCount, recipients.length, mailHost, mailPort);
        
        if (failCount > 0) {
            String errorMsg = String.format("Failed to send %d out of %d emails via SMTP %s:%d. Errors: %s", 
                    failCount, recipients.length, mailHost, mailPort, errors.toString());
            throw new RuntimeException(errorMsg);
        }
        
        return "bulk_sent:" + System.currentTimeMillis();
    }

    private String buildErrorMessage(MailException e, String recipient) {
        Throwable cause = e.getCause();
        
        if (cause instanceof ConnectException) {
            return String.format("Cannot connect to SMTP server %s:%d. " +
                    "Please check if SMTP server is running and accessible. " +
                    "If you're using MailHog for development, make sure it's running on port %d. " +
                    "If you're using a real SMTP server, check MAIL_HOST and MAIL_PORT environment variables.",
                    mailHost, mailPort, mailPort);
        } else if (cause instanceof SocketTimeoutException) {
            return String.format("SMTP server %s:%d connection timeout. " +
                    "Please check if SMTP server is accessible and network connection is stable.",
                    mailHost, mailPort);
        } else if (cause instanceof MessagingException) {
            MessagingException me = (MessagingException) cause;
            return String.format("SMTP messaging error: %s. " +
                    "Check SMTP server configuration (host, port, auth, SSL/TLS).",
                    me.getMessage());
        } else if (e.getMessage() != null) {
            if (e.getMessage().contains("Connection refused")) {
                return String.format("SMTP server %s:%d connection refused. " +
                        "Server is not running or not accessible. " +
                        "For development, you can use MailHog (docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog).",
                        mailHost, mailPort);
            } else if (e.getMessage().contains("Authentication failed")) {
                return String.format("SMTP authentication failed for %s:%d. " +
                        "Check MAIL_USERNAME and MAIL_PASSWORD environment variables.",
                        mailHost, mailPort);
            }
        }
        
        return e.getMessage() != null ? e.getMessage() : "Unknown error: " + e.getClass().getSimpleName();
    }
}

