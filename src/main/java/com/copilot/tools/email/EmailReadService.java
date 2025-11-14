package com.copilot.tools.email;

import com.copilot.auth.model.User;
import com.copilot.auth.repository.UserRepository;
import com.copilot.tools.email.dto.EmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Сервис для чтения писем из MailSlurp inbox через API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailReadService {

    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final EmailDigestService emailDigestService;
    private final DistributedLockService distributedLockService;

    @Value("${mailslurp.api-key}")
    private String mailslurpApiKey;

    private static final String MAILSLURP_API_BASE = "https://api.mailslurp.com";
    private static final String EMAIL_READ_LOCK_KEY = "email:read:all";

    @Scheduled(initialDelay = 300000, fixedRate = 3600000)
    public void readAndDigestEmailsForAllUsers() {
        if (!distributedLockService.tryLock(EMAIL_READ_LOCK_KEY)) {
            log.debug("Задача чтения писем уже выполняется на другом инстансе, пропускаем");
            return;
        }

        try {
            log.info("Начало чтения писем для всех пользователей");

            List<User> users = userRepository.findAllByEmailProviderIdIsNotNull();
            
            for (User user : users) {
                if (user.getEmailProviderId() == null || user.getEmail() == null) {
                    continue;
                }

                try {
                    readAndDigestEmailsForUser(user);
                } catch (Exception e) {
                    log.error("Ошибка при чтении писем для пользователя {}: {}", 
                            user.getEmail(), e.getMessage(), e);
                    // Продолжаем обработку других пользователей
                }
            }

            log.info("Завершено чтение писем для всех пользователей");
        } finally {
            distributedLockService.releaseLock(EMAIL_READ_LOCK_KEY);
        }
    }

    public void readAndDigestEmailsForUser(User user) {
        log.info("Чтение писем для пользователя: {}", user.getEmail());

        if (user.getEmailProviderId() == null || user.getEmailProviderId().trim().isEmpty()) {
            log.warn("У пользователя {} не установлен emailProviderId (MailSlurp inbox ID). " +
                    "Письма не могут быть прочитаны. Убедитесь, что пользователь был импортирован через массовый импорт.", 
                    user.getEmail());
            throw new RuntimeException("У пользователя не настроен MailSlurp inbox. " +
                    "Пользователь должен быть импортирован через массовый импорт для создания корпоративной почты.");
        }

        try {
            List<EmailMessage> emails = fetchEmailsFromMailSlurp(user.getEmailProviderId());

            if (emails.isEmpty()) {
                log.debug("Новых писем нет для пользователя: {}", user.getEmail());
                return;
            }

            log.info("Найдено {} новых писем для пользователя: {}", emails.size(), user.getEmail());

            emailDigestService.createAndCacheDigest(user.getId(), emails);

        } catch (Exception e) {
            log.error("Ошибка при чтении писем для пользователя {}: {}", 
                    user.getEmail(), e.getMessage(), e);
            throw new RuntimeException("Не удалось прочитать письма: " + e.getMessage(), e);
        }
    }

    private List<EmailMessage> fetchEmailsFromMailSlurp(String inboxId) {
        try {
            String getEmailsUrl = MAILSLURP_API_BASE + "/inboxes/" + inboxId + "/emails?unreadOnly=true";

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", mailslurpApiKey);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<Void> request = new HttpEntity<>(headers);

            org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>> typeRef = 
                    new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {};
            
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    getEmailsUrl,
                    HttpMethod.GET,
                    request,
                    typeRef
            );

            List<Map<String, Object>> emailsList = response.getBody();
            if (emailsList == null || emailsList.isEmpty()) {
                return Collections.emptyList();
            }

            return parseEmailsList(emailsList);

        } catch (Exception e) {
            log.error("Ошибка при получении писем из MailSlurp для inbox {}: {}", 
                    inboxId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<EmailMessage> parseEmailsList(List<Map<String, Object>> emailsList) {
        List<EmailMessage> emails = new ArrayList<>();
        for (Map<String, Object> emailData : emailsList) {
            EmailMessage email = parseEmailFromMailSlurp(emailData);
            if (email != null) {
                emails.add(email);
            }
        }
        return emails;
    }

    private EmailMessage parseEmailFromMailSlurp(Map<String, Object> emailData) {
        try {
            String id = (String) emailData.get("id");
            String from = extractEmailAddress(emailData.get("from"));
            String to = extractEmailAddress(emailData.get("to"));
            String subject = (String) emailData.get("subject");
            String body = (String) emailData.get("body");

            LocalDateTime receivedAt = null;
            Object createdAt = emailData.get("createdAt");
            if (createdAt != null) {
                if (createdAt instanceof String) {
                    receivedAt = LocalDateTime.parse(((String) createdAt).substring(0, 19));
                } else if (createdAt instanceof Number) {
                    receivedAt = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(((Number) createdAt).longValue()),
                            ZoneId.systemDefault()
                    );
                }
            }
            if (receivedAt == null) {
                receivedAt = LocalDateTime.now();
            }

            return new EmailMessage(id, from, to, subject, body, receivedAt);

        } catch (Exception e) {
            log.error("Ошибка при парсинге письма: {}", e.getMessage());
            return null;
        }
    }

    private String extractEmailAddress(Object emailObj) {
        if (emailObj == null) {
            return "unknown";
        }
        if (emailObj instanceof String) {
            return (String) emailObj;
        }
        if (emailObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> emailMap = (Map<String, Object>) emailObj;
            Object emailAddress = emailMap.get("emailAddress");
            if (emailAddress != null) {
                return emailAddress.toString();
            }
        }
        return emailObj.toString();
    }
}

