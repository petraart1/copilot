package com.copilot.tools.email;

import com.copilot.auth.model.User;
import com.copilot.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailForwardingService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    @Value("${spring.mail.username:assistant@company.com}")
    private String fromEmail;

    public void forwardEmailToPersonal(String corporateEmail, String fromEmailAddress, 
                                      String subject, String body) {
        User user = userRepository.findByEmail(corporateEmail)
                .orElse(null);

        if (user == null) {
            log.warn("Пользователь с корпоративной почтой {} не найден", corporateEmail);
            return;
        }

        String personalEmail = user.getPersonalEmail();
        if (personalEmail == null) {
            log.warn("У пользователя {} не указана личная почта", corporateEmail);
            return;
        }
        log.info("Пересылка письма с {} на личную почту {}", corporateEmail, personalEmail);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(personalEmail);
            message.setSubject("[Корпоративная почта] " + subject);
            
            String forwardedBody = String.format("""
                    Переслано с корпоративной почты: %s
                    
                    От: %s
                    Тема: %s
                    
                    ---
                    %s
                    """, corporateEmail, fromEmailAddress, subject, body);
            
            message.setText(forwardedBody);
            mailSender.send(message);
            
            log.info("Письмо переслано на личную почту: {}", personalEmail);
        } catch (Exception e) {
            log.error("Ошибка при пересылке письма на {}: {}", personalEmail, e.getMessage(), e);
        }
    }

    public void forwardAllNewEmails() {
        List<User> users = userRepository.findAllByPersonalEmailIsNotNull();
        
        for (User user : users) {
            if (user.getPersonalEmail() == null || user.getEmail() == null) {
                continue;
            }
            
            // TODO: Читать письма через IMAP для корпоративной почты (user.getEmail())
            // и пересылать их на личную почту (user.getPersonalEmail())
            // Это будет реализовано в EmailReadService
        }
    }
}

