package com.copilot.tools.email;

import com.copilot.tools.email.dto.EmailDigest;
import com.copilot.tools.email.dto.EmailMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDigestService {

    private final ChatModel chatModel;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String DIGEST_CACHE_KEY_PREFIX = "email:digest:";
    private static final Duration DIGEST_CACHE_TTL = Duration.ofHours(1);

    public EmailDigest createAndCacheDigest(java.util.UUID userId, List<EmailMessage> emails) {
        log.info("Создание digest для пользователя {} из {} писем", userId, emails.size());

        if (emails.isEmpty()) {
            EmailDigest emptyDigest = new EmailDigest(
                    "Новых писем нет",
                    0,
                    LocalDateTime.now(),
                    null
            );
            cacheDigest(userId, emptyDigest);
            return emptyDigest;
        }

        try {
            String emailsText = formatEmailsForLLM(emails);

            String summary = generateSummary(emailsText);

            LocalDateTime lastEmailAt = emails.stream()
                    .map(EmailMessage::receivedAt)
                    .max(Comparator.naturalOrder())
                    .orElse(LocalDateTime.now());

            EmailDigest digest = new EmailDigest(
                    summary,
                    emails.size(),
                    LocalDateTime.now(),
                    lastEmailAt
            );

            cacheDigest(userId, digest);

            log.info("Digest создан для пользователя {}: {} писем", userId, emails.size());
            return digest;

        } catch (Exception e) {
            log.error("Ошибка при создании digest для пользователя {}: {}", 
                    userId, e.getMessage(), e);
            throw new RuntimeException("Не удалось создать digest: " + e.getMessage(), e);
        }
    }

    public EmailDigest getCachedDigest(java.util.UUID userId) {
        String cacheKey = DIGEST_CACHE_KEY_PREFIX + userId;
        String cachedSummary = redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedSummary == null) {
            return null;
        }

        return new EmailDigest(
                cachedSummary,
                0,
                LocalDateTime.now(),
                null
        );
    }

    private String formatEmailsForLLM(List<EmailMessage> emails) {
        StringBuilder sb = new StringBuilder();
        sb.append("Вот список новых писем:\n\n");
        
        for (EmailMessage email : emails) {
            sb.append("От: ").append(email.from()).append("\n");
            sb.append("Тема: ").append(email.subject()).append("\n");
            sb.append("Дата: ").append(email.receivedAt()).append("\n");
            if (email.body() != null && !email.body().isEmpty()) {
                String body = email.body();
                if (body.length() > 500) {
                    body = body.substring(0, 500) + "...";
                }
                sb.append("Содержание: ").append(body).append("\n");
            }
            sb.append("---\n");
        }

        return sb.toString();
    }

    private String generateSummary(String emailsText) {
        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from("""
                                    Ты помощник для обработки email писем. 
                                    Проанализируй список писем и создай краткую сводку на русском языке.
                                    Укажи основные темы, важные события и действия, которые требуются.
                                    Будь кратким и конкретным.
                                    """),
                            UserMessage.from(emailsText)
                    ))
                    .build();

            ChatResponse response = chatModel.chat(request);
            String summary = response.aiMessage().text();
            
            if (summary == null || summary.isEmpty()) {
                return "Не удалось создать сводку писем";
            }

            return summary;

        } catch (Exception e) {
            log.error("Ошибка при генерации summary через LLM: {}", e.getMessage(), e);
            return "Ошибка при создании сводки: " + e.getMessage();
        }
    }

    private void cacheDigest(java.util.UUID userId, EmailDigest digest) {
        try {
            String cacheKey = DIGEST_CACHE_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(cacheKey, digest.summary(), DIGEST_CACHE_TTL);
            log.debug("Digest закэширован для пользователя {} на {} часов", userId, DIGEST_CACHE_TTL.toHours());
        } catch (Exception e) {
            log.error("Ошибка при кэшировании digest для пользователя {}: {}", 
                    userId, e.getMessage(), e);
        }
    }
}

