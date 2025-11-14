package com.copilot.llm.tools;

import com.copilot.auth.repository.UserRepository;
import com.copilot.tools.email.EmailService;
import com.copilot.tools.meeting.MeetingService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Инструменты для AI-агента, зарегистрированные через LangChain4j аннотации @Tool
 * 
 * ВАЖНО: Для использования этих инструментов нужно:
 * 1. Создать ToolSpecification из этих методов
 * 2. Передать их в ChatModel через ChatRequest.tools()
 * 
 * Однако, текущая версия LangChain4j может не поддерживать автоматическую регистрацию через @Tool,
 * поэтому пока используем текстовое описание в промпте и парсинг ответов LLM.
 * 
 * В будущем можно использовать эти методы напрямую через рефлексию.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTools {

    private final MeetingService meetingService;
    private final EmailService emailService;
    private final UserRepository userRepository;

    /**
     * Запланировать встречу (создать Jitsi ссылку, добавить в календарь, отправить приглашения)
     * 
     * @param title название встречи
     * @param startTime время начала встречи в формате ISO 8601 (например: 2025-11-14T16:00:00)
     * @param durationMinutes длительность встречи в минутах (по умолчанию 60)
     * @param attendees список email адресов участников
     * @param description описание встречи (опционально)
     * @param organizerEmail email организатора встречи (автоматически определяется из контекста пользователя)
     * @return результат создания встречи
     */
    @Tool("Запланировать встречу: создать Jitsi ссылку, добавить в календарь, отправить приглашения. " +
          "Параметры: title (string) - название встречи, " +
          "start_time (ISO 8601) - время начала, " +
          "duration_minutes (integer, default 60) - длительность, " +
          "attendees (array of emails) - участники, " +
          "description (string, optional) - описание. " +
          "Email организатора определяется автоматически из контекста пользователя.")
    public String scheduleMeeting(
            String title,
            String startTime,
            Integer durationMinutes,
            List<String> attendees,
            String description,
            String organizerEmail
    ) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            if (durationMinutes == null) {
                durationMinutes = 60;
            }

            MeetingService.MeetingResponse response = meetingService.scheduleMeeting(
                    title,
                    start,
                    durationMinutes,
                    attendees,
                    description,
                    organizerEmail
            );

            // MeetingResponse имеет @Data, поэтому геттеры доступны
            return String.format("Встреча '%s' запланирована на %s. Ссылка: %s. Участники: %s",
                    title, startTime, response.getMeetingUrl(), String.join(", ", attendees));
        } catch (Exception e) {
            log.error("Ошибка при создании встречи: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось создать встречу: " + e.getMessage(), e);
        }
    }

    /**
     * Отправить email уведомление одному или нескольким адресатам
     * 
     * @param recipients список email адресов получателей
     * @param subject тема письма
     * @param message текст письма
     * @return результат отправки
     */
    @Tool("Отправить email уведомление одному или нескольким адресатам. " +
          "Параметры: recipients (array of emails) - получатели, " +
          "subject (string) - тема, " +
          "message (string) - текст письма.")
    public String sendNotification(
            List<String> recipients,
            String subject,
            String message
    ) {
        try {
            // Валидация получателей
            List<String> validRecipients = recipients.stream()
                    .filter(email -> email != null && !email.isEmpty())
                    .filter(email -> userRepository.existsByEmail(email))
                    .toList();

            if (validRecipients.isEmpty()) {
                return "Ошибка: не найдено ни одного валидного получателя из списка: " + recipients;
            }

            String[] recipientsArray = validRecipients.toArray(new String[0]);
            emailService.sendBulkEmails(recipientsArray, subject, message);

            return String.format("Уведомления отправлены %d получателям: %s",
                    validRecipients.size(), String.join(", ", validRecipients));
        } catch (Exception e) {
            log.error("Ошибка при отправке уведомлений: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось отправить уведомления: " + e.getMessage(), e);
        }
    }

    /**
     * Составить текст письма (без отправки)
     * 
     * @param recipient email получателя
     * @param subject тема письма
     * @param content текст письма
     * @return составленный текст письма
     */
    @Tool("Составить текст письма без отправки. " +
          "Параметры: recipient (email) - получатель, " +
          "subject (string) - тема, " +
          "content (string) - текст письма.")
    public String composeLetter(
            String recipient,
            String subject,
            String content
    ) {
        return String.format("""
                Письмо для: %s
                Тема: %s
                
                %s
                """, recipient, subject, content);
    }
}

