package com.copilot.tools.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailProvisioningService {

    private final RestTemplate restTemplate;

    @Value("${mailslurp.api-key}")
    private String mailslurpApiKey;

    private static final String MAILSLURP_API_URL = "https://api.mailslurp.com/inboxes";

    public MailSlurpInboxResponse createCorporateEmail() {
        log.info("Создание корпоративной почты через MailSlurp");

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("inboxType", "SMTP_INBOX");

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", mailslurpApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    MAILSLURP_API_URL,
                    HttpMethod.POST,
                    request,
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("MailSlurp вернул пустой ответ");
            }

            String inboxId = (String) responseBody.get("id");
            String emailAddress = (String) responseBody.get("emailAddress");
            String inboxType = (String) responseBody.get("inboxType");

            if (inboxId == null || emailAddress == null) {
                throw new RuntimeException("MailSlurp вернул неполный ответ: " + responseBody);
            }

            MailSlurpInboxResponse inbox = new MailSlurpInboxResponse(inboxId, emailAddress, inboxType);
            log.info("Корпоративная почта создана: {} (ID: {})", inbox.emailAddress(), inbox.id());
            return inbox;

        } catch (Exception e) {
            log.error("Ошибка при создании корпоративной почты через MailSlurp: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось создать корпоративную почту: " + e.getMessage(), e);
        }
    }

    public void sendEmailToInbox(String inboxEmail, String inboxId, String subject, String body) {
        log.info("Отправка письма на MailSlurp inbox: {}", inboxEmail);

        try {
            String finalInboxId = inboxId;
            if (finalInboxId == null) {
                finalInboxId = findInboxIdByEmail(inboxEmail);
                if (finalInboxId == null) {
                    throw new RuntimeException("Inbox не найден для email: " + inboxEmail);
                }
            }

            String sendEmailUrl = "https://api.mailslurp.com/sendEmail";
            
            Map<String, Object> emailBody = new HashMap<>();
            emailBody.put("inboxId", finalInboxId);
            emailBody.put("to", inboxEmail);
            emailBody.put("subject", subject);
            emailBody.put("body", body);
            emailBody.put("isHTML", false);

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", mailslurpApiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(emailBody, headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    sendEmailUrl,
                    HttpMethod.POST,
                    request,
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Письмо успешно отправлено на MailSlurp inbox: {}", inboxEmail);
            } else {
                throw new RuntimeException("Ошибка при отправке письма: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Ошибка при отправке письма на MailSlurp inbox {}: {}", inboxEmail, e.getMessage(), e);
            throw new RuntimeException("Не удалось отправить письмо на MailSlurp inbox: " + e.getMessage(), e);
        }
    }

    private String findInboxIdByEmail(String email) {
        try {
            String getInboxesUrl = "https://api.mailslurp.com/inboxes?page=0&size=100";

            HttpHeaders headers = new HttpHeaders();
            headers.set("x-api-key", mailslurpApiKey);

            HttpEntity<Void> request = new HttpEntity<>(headers);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    getInboxesUrl,
                    HttpMethod.GET,
                    request,
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> content = (List<Map<String, Object>>) responseBody.get("content");
                if (content != null) {
                    for (Map<String, Object> inbox : content) {
                        if (email.equals(inbox.get("emailAddress"))) {
                            return (String) inbox.get("id");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при поиске inbox по email {}: {}", email, e.getMessage());
        }
        return null;
    }

    public record MailSlurpInboxResponse(
            String id,
            String emailAddress,
            String inboxType
    ) {
    }
}

