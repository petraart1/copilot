package com.copilot.tools.meeting;

import com.copilot.tools.calendar.CalendarService;
import com.copilot.tools.calendar.dto.CreateEventRequest;
import com.copilot.tools.calendar.dto.EventResponse;
import com.copilot.tools.email.EmailService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingService {

    private final EmailService emailService;
    private final CalendarService calendarService;

    @Value("${meetings.jitsi.base-url:https://meet.jit.si}")
    private String jitsiBaseUrl;

    public MeetingResponse scheduleMeeting(
            String title,
            LocalDateTime startTime,
            Integer durationMinutes,
            List<String> attendees,
            String description,
            String organizerEmail
    ) {
        log.info("Создание встречи '{}' для {} участников", title, attendees.size());

        try {
            String roomId = generateJitsiRoomId(title);
            String meetingUrl = jitsiBaseUrl + "/" + roomId;

            String eventDescription = description != null && !description.isEmpty()
                    ? description + "\n\nСсылка на видеовстречу: " + meetingUrl
                    : "Ссылка на видеовстречу: " + meetingUrl;

            CreateEventRequest eventRequest = new CreateEventRequest(
                    title,
                    startTime,
                    durationMinutes,
                    eventDescription,
                    attendees,
                    meetingUrl
            );

            EventResponse eventResponse = calendarService.createEvent(eventRequest, organizerEmail);
            log.info("Событие создано в календаре: {}", eventResponse.eventId());

            sendMeetingInvitations(title, startTime, durationMinutes, meetingUrl, description, attendees);

            log.info("Встреча создана: {} в {} для {} участников", title, startTime, attendees.size());
            return new MeetingResponse(
                    "success",
                    roomId,
                    meetingUrl,
                    startTime.toString(),
                    attendees.toArray(new String[0])
            );

        } catch (Exception e) {
            log.error("Ошибка при создании встречи: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось создать встречу: " + e.getMessage(), e);
        }
    }

    private String generateJitsiRoomId(String title) {
        String sanitized = title.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        return sanitized + "-" + randomSuffix;
    }

    /**
     * Отправляет приглашения на встречу всем участникам
     */
    private void sendMeetingInvitations(String title, LocalDateTime startTime, Integer durationMinutes,
                                       String meetingUrl, String description, List<String> attendees) {
        String notificationBody = buildMeetingInvitationBody(title, startTime, durationMinutes, meetingUrl, description);

        log.info("Отправка приглашений на встречу '{}' для {} участников", title, attendees.size());
        
        for (String attendee : attendees) {
            try {
                log.info("Отправка приглашения участнику: {}", attendee);
                emailService.sendEmail(
                        attendee,
                        "Приглашение на встречу: " + title,
                        notificationBody
                );
                log.info("Приглашение успешно отправлено участнику: {}", attendee);
            } catch (Exception e) {
                log.error("Не удалось отправить приглашение участнику {}: {}", attendee, e.getMessage(), e);
            }
        }
        
        log.info("Отправка приглашений завершена для встречи '{}'", title);
    }

    /**
     * Формирует текст приглашения на встречу
     */
    private String buildMeetingInvitationBody(String title, LocalDateTime startTime, Integer durationMinutes,
                                             String meetingUrl, String description) {
        return String.format("""
                Вы приглашены на встречу: %s
                
                Время: %s
                Длительность: %d минут
                
                Ссылка на видеовстречу (Jitsi Meet): %s
                
                %s
                
                Примечание: Для участия в видеовстрече просто перейдите по ссылке. 
                Регистрация не требуется.
                
                С уважением,
                Business Assistant
                """, 
                title, 
                startTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
                durationMinutes,
                meetingUrl,
                description != null && !description.isEmpty() ? "Описание: " + description : "");
    }

    /**
     * Генерирует ссылку на Jitsi Meet встречу
     */
    public String generateJitsiLink(String roomName) {
        String roomId = generateJitsiRoomId(roomName);
        return jitsiBaseUrl + "/" + roomId;
    }

    @Data
    public static class MeetingResponse {
        private final String status;
        private final String roomId;
        private final String meetingUrl;
        private final String startTime;
        private final String[] attendees;
    }
}

