package com.copilot.tools.calendar;

import com.copilot.auth.model.User;
import com.copilot.auth.repository.UserRepository;
import com.copilot.tools.calendar.dto.CreateEventRequest;
import com.copilot.tools.calendar.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Сервис для работы с событиями календаря через CalDAV (Radicale)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final UserRepository userRepository;
    private final CalendarProvisioningService calendarProvisioningService;

    @Value("${calendar.caldav.base-url:http://calendar:5232}")
    private String caldavBaseUrl;

    /**
     * Создает событие в календаре для всех участников
     */
    public EventResponse createEvent(CreateEventRequest request, String organizerEmail) {
        log.info("Создание события '{}' для {} участников", request.title(), request.attendeeEmails().size());

        // Проверяем, что организатор существует
        if (!userRepository.existsByEmail(organizerEmail)) {
            throw new RuntimeException("Организатор не найден: " + organizerEmail);
        }

        // Генерируем уникальный ID события
        String eventId = UUID.randomUUID().toString();
        LocalDateTime endTime = request.startTime().plusMinutes(request.durationMinutes());

        // Создаем событие для каждого участника
        for (String attendeeEmail : request.attendeeEmails()) {
            User attendee = userRepository.findByEmail(attendeeEmail)
                    .orElse(null);

            if (attendee == null) {
                log.warn("Участник {} не найден, пропускаем", attendeeEmail);
                continue;
            }

            try {
                createEventInCalendar(attendee, request, eventId, organizerEmail, endTime);
                log.info("Событие создано в календаре участника: {}", attendeeEmail);
            } catch (Exception e) {
                // Если у пользователя нет календаря (нет calendarPassword), пропускаем его
                if (e.getMessage() != null && e.getMessage().contains("не настроен пароль календаря")) {
                    log.warn("У участника {} не настроен календарь (нет calendarPassword). " +
                            "Событие не будет добавлено в его календарь, но приглашение отправлено. " +
                            "Участник может добавить событие вручную или настроить календарь через импорт пользователей.", 
                            attendeeEmail);
                } else {
                    log.error("Ошибка при создании события в календаре участника {}: {}", 
                            attendeeEmail, e.getMessage(), e);
                }
                // Продолжаем создавать события для других участников
            }
        }

        return new EventResponse(
                eventId,
                request.title(),
                request.startTime(),
                endTime,
                request.description(),
                request.attendeeEmails(),
                request.location(),
                null // calendarUrl можно добавить позже
        );
    }

    /**
     * Создает событие в календаре конкретного пользователя через CalDAV PUT
     */
    private void createEventInCalendar(User user, CreateEventRequest request, 
                                      String eventId, String organizerEmail, 
                                      LocalDateTime endTime) {
        // Получаем пароль для календаря (не зашифрован, так как нужен для аутентификации в Radicale)
        String calendarPassword = user.getCalendarPassword();
        if (calendarPassword == null) {
            throw new RuntimeException("У пользователя " + user.getEmail() + " не настроен пароль календаря");
        }

        // Формируем URL события в календаре
        // ВАЖНО: Используем ту же логику, что и при создании календаря в CalendarProvisioningService
        String username = extractUsernameFromEmail(user.getEmail());
        String calendarName = sanitizeCalendarName(user.getFirstName() != null && !user.getFirstName().isEmpty()
                ? user.getFirstName() + "'s Calendar" 
                : "Work Calendar");
        String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String encodedCalendarName = URLEncoder.encode(calendarName, StandardCharsets.UTF_8);
        String eventUrl = caldavBaseUrl + "/" + encodedUsername + "/" + encodedCalendarName + "/" + eventId + ".ics";
        
        log.info("Создание события в календаре для пользователя {}: URL={}, calendarName={}, firstName={}", 
                user.getEmail(), eventUrl, calendarName, user.getFirstName());

        // Генерируем iCalendar формат
        String icalContent = generateICalendarContent(
                eventId,
                request.title(),
                request.startTime(),
                endTime,
                request.description(),
                request.attendeeEmails(),
                organizerEmail,
                request.location()
        );

        try {
            @SuppressWarnings("deprecation")
            URL url = new URL(eventUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            // Basic Auth
            String auth = user.getEmail() + ":" + calendarPassword;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
            connection.setRequestProperty("Content-Type", "text/calendar; charset=utf-8");

            // Отправляем iCalendar контент
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = icalContent.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            String responseMessage = connection.getResponseMessage();
            
            log.info("Ответ от CalDAV при создании события {}: {} {} (URL: {})", 
                    eventId, responseCode, responseMessage, eventUrl);

            if (responseCode == HttpURLConnection.HTTP_CREATED || responseCode == 201 || responseCode == 204) {
                log.info("Событие успешно создано в календаре: {}", eventUrl);
            } else if (responseCode == 409) {
                // Событие уже существует - это нормально, считаем успехом
                log.info("Событие уже существует в календаре (409): {}. Продолжаем выполнение.", eventUrl);
            } else if (responseCode == 404) {
                // Календарь не найден - создаем календарь автоматически
                String errorMessage = readErrorResponse(connection);
                log.warn("Календарь не найден (404) для пользователя {}: {}. URL: {}. Создаем календарь автоматически.", 
                        user.getEmail(), errorMessage, eventUrl);
                
                // Создаем календарь автоматически
                try {
                    calendarProvisioningService.createCalendar(
                            user.getEmail(),
                            calendarPassword,
                            user.getFirstName()
                    );
                    log.info("Календарь автоматически создан для пользователя: {}", user.getEmail());
                    
                    // Повторяем попытку создания события (рекурсивный вызов)
                    createEventInCalendar(user, request, eventId, organizerEmail, endTime);
                    return;
                } catch (Exception e) {
                    log.error("Не удалось создать календарь для пользователя {}: {}", user.getEmail(), e.getMessage(), e);
                    throw new RuntimeException("Календарь не найден и не удалось создать его для пользователя " + user.getEmail() + 
                            ". URL: " + eventUrl + ". Ошибка: " + e.getMessage());
                }
            } else if (responseCode == 401 || responseCode == 403) {
                // Ошибка аутентификации
                String errorMessage = readErrorResponse(connection);
                log.error("Ошибка аутентификации ({}) при создании события в календаре {}: {}", 
                        responseCode, eventUrl, errorMessage);
                throw new RuntimeException("Ошибка аутентификации при создании события в календаре: " + responseCode);
            } else {
                String errorMessage = readErrorResponse(connection);
                log.error("Ошибка при создании события в календаре: {} - {} (URL: {})", 
                        responseCode, errorMessage, eventUrl);
                throw new RuntimeException("Не удалось создать событие в календаре: " + responseCode + " - " + errorMessage);
            }

        } catch (Exception e) {
            log.error("Ошибка при создании события через CalDAV: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось создать событие через CalDAV: " + e.getMessage(), e);
        }
    }

    /**
     * Генерирует iCalendar (RFC 5545) контент для события
     */
    private String generateICalendarContent(String eventId, String title, 
                                           LocalDateTime startTime, LocalDateTime endTime,
                                           String description, List<String> attendeeEmails,
                                           String organizerEmail, String location) {
        // Форматируем даты в формат iCalendar (UTC)
        DateTimeFormatter icalFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        String dtStart = startTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC"))
                .format(icalFormatter);
        String dtEnd = endTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC"))
                .format(icalFormatter);
        String dtStamp = LocalDateTime.now().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC"))
                .format(icalFormatter);

        StringBuilder ical = new StringBuilder();
        ical.append("BEGIN:VCALENDAR\r\n");
        ical.append("VERSION:2.0\r\n");
        ical.append("PRODID:-//Business Assistant//Calendar Service//EN\r\n");
        ical.append("CALSCALE:GREGORIAN\r\n");
        ical.append("METHOD:REQUEST\r\n");
        ical.append("BEGIN:VEVENT\r\n");
        ical.append("UID:").append(eventId).append("@business-assistant.local\r\n");
        ical.append("DTSTAMP:").append(dtStamp).append("\r\n");
        ical.append("DTSTART:").append(dtStart).append("\r\n");
        ical.append("DTEND:").append(dtEnd).append("\r\n");
        ical.append("SUMMARY:").append(escapeICalText(title)).append("\r\n");
        
        if (description != null && !description.isEmpty()) {
            ical.append("DESCRIPTION:").append(escapeICalText(description)).append("\r\n");
        }
        
        if (location != null && !location.isEmpty()) {
            ical.append("LOCATION:").append(escapeICalText(location)).append("\r\n");
        }
        
        ical.append("ORGANIZER;CN=").append(escapeICalText(organizerEmail))
                .append(":MAILTO:").append(organizerEmail).append("\r\n");
        
        // Добавляем участников
        for (String attendeeEmail : attendeeEmails) {
            ical.append("ATTENDEE;CN=").append(escapeICalText(attendeeEmail))
                    .append(";RSVP=TRUE:MAILTO:").append(attendeeEmail).append("\r\n");
        }
        
        ical.append("STATUS:CONFIRMED\r\n");
        ical.append("SEQUENCE:0\r\n");
        ical.append("END:VEVENT\r\n");
        ical.append("END:VCALENDAR\r\n");

        return ical.toString();
    }

    /**
     * Экранирует специальные символы для iCalendar формата
     */
    private String escapeICalText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace(";", "\\;")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    /**
     * Извлекает username из email для Radicale
     */
    private String extractUsernameFromEmail(String email) {
        return email;
    }

    /**
     * Очищает имя календаря от недопустимых символов
     */
    private String sanitizeCalendarName(String name) {
        // Транслитерируем русские буквы в латиницу для совместимости с файловой системой и URL
        String transliterated = transliterateRussianToLatin(name);
        // Удаляем недопустимые символы для URL и файловой системы (оставляем только латиницу, цифры, пробелы и дефисы)
        String sanitized = transliterated.replaceAll("[^a-zA-Z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .toLowerCase();
        // Если после санитизации имя пустое или слишком короткое, используем дефолтное имя
        if (sanitized.isEmpty() || sanitized.length() < 2) {
            sanitized = "calendar";
        }
        return sanitized;
    }
    
    /**
     * Транслитерирует русские буквы в латиницу
     * ВАЖНО: Использует ту же логику, что и CalendarProvisioningService
     */
    private String transliterateRussianToLatin(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        // Простая транслитерация для основных русских букв
        return text
                .replace("А", "A").replace("а", "a")
                .replace("Б", "B").replace("б", "b")
                .replace("В", "V").replace("в", "v")
                .replace("Г", "G").replace("г", "g")
                .replace("Д", "D").replace("д", "d")
                .replace("Е", "E").replace("е", "e")
                .replace("Ё", "E").replace("ё", "e")
                .replace("Ж", "Zh").replace("ж", "zh")
                .replace("З", "Z").replace("з", "z")
                .replace("И", "I").replace("и", "i")
                .replace("Й", "Y").replace("й", "y")
                .replace("К", "K").replace("к", "k")
                .replace("Л", "L").replace("л", "l")
                .replace("М", "M").replace("м", "m")
                .replace("Н", "N").replace("н", "n")
                .replace("О", "O").replace("о", "o")
                .replace("П", "P").replace("п", "p")
                .replace("Р", "R").replace("р", "r")
                .replace("С", "S").replace("с", "s")
                .replace("Т", "T").replace("т", "t")
                .replace("У", "U").replace("у", "u")
                .replace("Ф", "F").replace("ф", "f")
                .replace("Х", "Kh").replace("х", "kh")
                .replace("Ц", "Ts").replace("ц", "ts")
                .replace("Ч", "Ch").replace("ч", "ch")
                .replace("Ш", "Sh").replace("ш", "sh")
                .replace("Щ", "Shch").replace("щ", "shch")
                .replace("Ъ", "").replace("ъ", "")
                .replace("Ы", "Y").replace("ы", "y")
                .replace("Ь", "").replace("ь", "")
                .replace("Э", "E").replace("э", "e")
                .replace("Ю", "Yu").replace("ю", "yu")
                .replace("Я", "Ya").replace("я", "ya");
    }

    /**
     * Читает ответ об ошибке из HttpURLConnection
     */
    private String readErrorResponse(HttpURLConnection connection) {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        } catch (Exception e) {
            return "Не удалось прочитать ответ об ошибке";
        }
    }
}

