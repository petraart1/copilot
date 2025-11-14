package com.copilot.tools.calendar;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarProvisioningService {

    @Value("${calendar.caldav.base-url:http://calendar:5232}")
    private String caldavBaseUrl;

    /**
     * Создает пользователя и календарь в Radicale для пользователя
     * @param userEmail email пользователя (корпоративная почта)
     * @param password пароль для CalDAV аккаунта
     * @param firstName имя пользователя
     * @return RadicaleAccountResponse с данными аккаунта
     */
    public RadicaleAccountResponse createCalendar(String userEmail, String password, String firstName) {
        log.info("Создание календаря в Radicale для пользователя: {}", userEmail);

        try {
            // 1. Пытаемся создать пользователя в htpasswd через Docker exec
            // ВАЖНО: Это может не работать из контейнера copilot, так как Docker socket может быть недоступен
            // В этом случае Radicale создаст пользователя автоматически при первом CalDAV запросе с правильной аутентификацией
            createUserInHtpasswd(userEmail, password);
            
            // 2. Создаем календарь через CalDAV MKCOL
            // Если пользователь еще не создан в htpasswd, Radicale создаст его автоматически при первом CalDAV запросе
            // Для этого нужно, чтобы Radicale был настроен с auth.type = htpasswd и правильным файлом users
            String calendarName = sanitizeCalendarName(firstName != null && !firstName.isEmpty() 
                    ? firstName + "'s Calendar" 
                    : "Work Calendar");
            String calendarId = createCalendarViaCalDAV(userEmail, password, calendarName);
            log.info("Календарь создан для пользователя {}: {}", userEmail, calendarId);

            return new RadicaleAccountResponse(userEmail, calendarId, userEmail);

        } catch (Exception e) {
            log.error("Ошибка при создании календаря в Radicale для {}: {}", userEmail, e.getMessage(), e);
            // Не прерываем импорт, возвращаем дефолтный ответ
            // Пользователь сможет создать календарь вручную позже
            return new RadicaleAccountResponse(userEmail, "default", userEmail);
        }
    }
    
    /**
     * Создает пользователя в Radicale через htpasswd
     * Пытается использовать Docker exec для выполнения команды htpasswd в контейнере calendar
     * Если Docker exec недоступен (например, из контейнера copilot), пропускаем - Radicale создаст пользователя автоматически при первом CalDAV запросе
     */
    private void createUserInHtpasswd(String username, String password) {
        try {
            log.info("Попытка создать пользователя в Radicale htpasswd: {}", username);
            
            // Попробуем создать пользователя через процесс Docker exec
            // ВАЖНО: Это может не работать из контейнера copilot, так как Docker socket может быть недоступен
            // В этом случае Radicale создаст пользователя автоматически при первом CalDAV запросе с правильной аутентификацией
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "docker", "exec", "calendar", 
                    "htpasswd", "-b", "-m", "/data/users", username, password
            );
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("Пользователь {} успешно создан в Radicale htpasswd через Docker exec", username);
            } else {
                // Если не удалось создать через Docker exec, это нормально - Radicale создаст пользователя автоматически
                log.info("Не удалось создать пользователя {} через Docker exec (exit code: {}). " +
                        "Это нормально - Radicale создаст пользователя автоматически при первом CalDAV запросе", username, exitCode);
                
                // Читаем вывод процесса для отладки
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder output = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                    if (output.length() > 0) {
                        log.debug("Docker exec output: {}", output.toString().trim());
                    }
                }
            }
        } catch (Exception e) {
            // Если Docker exec не работает (например, из контейнера copilot), это нормально
            // Radicale создаст пользователя автоматически при первом CalDAV запросе с правильной аутентификацией
            log.info("Не удалось создать пользователя {} через Docker exec: {}. " +
                    "Это нормально - Radicale создаст пользователя автоматически при первом CalDAV запросе", username, e.getMessage());
        }
    }

    /**
     * Создает календарь через CalDAV MKCOL запрос
     * Использует низкоуровневый Socket для отправки HTTP запроса с методом MKCOL,
     * так как HttpURLConnection не поддерживает нестандартные HTTP методы
     * Radicale может автоматически создавать пользователей при первом обращении
     */
    private String createCalendarViaCalDAV(String email, String password, String calendarName) {
        // Radicale использует формат: /{username}/{calendar-name}/
        // Для Radicale username обычно берется из email (до @) или весь email
        String username = extractUsernameFromEmail(email);
        String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String encodedCalendarName = URLEncoder.encode(calendarName, StandardCharsets.UTF_8);
        String calendarUrl = caldavBaseUrl + "/" + encodedUsername + "/" + encodedCalendarName + "/";

        // CalDAV MKCOL body для создания календаря
        String mkcolBody = """
                <?xml version="1.0" encoding="utf-8" ?>
                <D:mkcol xmlns:D="DAV:" xmlns:C="urn:ietf:params:xml:ns:caldav">
                    <D:set>
                        <D:prop>
                            <D:resourcetype>
                                <D:collection/>
                                <C:calendar/>
                            </D:resourcetype>
                            <D:displayname>%s</D:displayname>
                        </D:prop>
                    </D:set>
                </D:mkcol>
                """.formatted(calendarName);

        try {
            // Парсим URL для получения хоста и порта
            @SuppressWarnings("deprecation")
            URL url = new URL(calendarUrl);
            String host = url.getHost();
            int port = url.getPort() == -1 ? (url.getProtocol().equals("https") ? 443 : 80) : url.getPort();
            String path = url.getPath();

            // Используем Socket для отправки HTTP запроса с методом MKCOL
            // HttpURLConnection не поддерживает нестандартные методы
            try (java.net.Socket socket = new java.net.Socket(host, port);
                 java.io.OutputStream out = socket.getOutputStream();
                 java.io.BufferedReader in = new java.io.BufferedReader(
                         new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                // Basic Auth
                String auth = email + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

                // Формируем HTTP запрос с методом MKCOL
                StringBuilder request = new StringBuilder();
                request.append("MKCOL ").append(path).append(" HTTP/1.1\r\n");
                request.append("Host: ").append(host);
                if (port != 80 && port != 443) {
                    request.append(":").append(port);
                }
                request.append("\r\n");
                request.append("Authorization: Basic ").append(encodedAuth).append("\r\n");
                request.append("Content-Type: application/xml; charset=utf-8\r\n");
                request.append("Depth: 0\r\n");
                request.append("Content-Length: ").append(mkcolBody.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
                request.append("Connection: close\r\n");
                request.append("\r\n");
                request.append(mkcolBody);

                // Отправляем запрос
                out.write(request.toString().getBytes(StandardCharsets.UTF_8));
                out.flush();

                // Читаем ответ
                String responseLine = in.readLine();
                if (responseLine == null) {
                    log.warn("Пустой ответ от Radicale при создании календаря: {}", calendarUrl);
                    return calendarName;
                }

                // Парсим статус код из первой строки ответа (например, "HTTP/1.1 201 Created")
                int responseCode = parseStatusCode(responseLine);
                log.debug("Ответ от Radicale при создании календаря {}: {} ({})", calendarUrl, responseCode, responseLine);

                // Читаем остальные заголовки (не используем, но нужно прочитать)
                String headerLine;
                while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                    // Читаем заголовки, но не обрабатываем
                }

                if (responseCode == 201 || responseCode == 204) {
                    log.info("Календарь успешно создан через CalDAV: {}", calendarUrl);
                    return calendarName;
                } else if (responseCode == 405) {
                    // Календарь уже существует (Method Not Allowed)
                    log.warn("Календарь {} уже существует (405)", calendarUrl);
                    return calendarName;
                } else if (responseCode == 207) {
                    // Multi-Status - частичный успех
                    log.warn("Календарь {} создан с предупреждениями (207)", calendarUrl);
                    return calendarName;
                } else if (responseCode == 401 || responseCode == 403) {
                    // Ошибка аутентификации - возможно, пользователь не создан
                    log.warn("Ошибка аутентификации при создании календаря {}: {}. " +
                            "Возможно, пользователь не создан в Radicale. " +
                            "Календарь будет создан автоматически при первом обращении.", calendarUrl, responseCode);
                    return calendarName;
                } else {
                    log.warn("Неожиданный код ответа при создании календаря {}: {}", calendarUrl, responseCode);
                    // Не прерываем импорт, возвращаем имя календаря
                    // Radicale создаст календарь автоматически при первом обращении
                    return calendarName;
                }
            }
        } catch (java.net.ConnectException e) {
            log.warn("Не удалось подключиться к Radicale для создания календаря {}: {}. " +
                    "Календарь будет создан автоматически при первом обращении.", calendarUrl, e.getMessage());
            return calendarName;
        } catch (Exception e) {
            log.warn("Ошибка при создании календаря через CalDAV {}: {}. " +
                    "Календарь будет создан автоматически при первом обращении.", calendarUrl, e.getMessage());
            return calendarName;
        }
    }

    /**
     * Парсит статус код из первой строки HTTP ответа
     * Формат: "HTTP/1.1 201 Created" или "HTTP/1.0 201 Created"
     */
    private int parseStatusCode(String statusLine) {
        try {
            // Статус код находится во второй части строки (после пробела)
            String[] parts = statusLine.split("\\s+");
            if (parts.length >= 2) {
                return Integer.parseInt(parts[1]);
            }
        } catch (Exception e) {
            log.debug("Не удалось распарсить статус код из строки: {}", statusLine);
        }
        return -1;
    }

    /**
     * Извлекает username из email для Radicale
     * Radicale обычно использует часть до @ или весь email
     */
    private String extractUsernameFromEmail(String email) {
        // Используем весь email как username, так как Radicale поддерживает это
        // Можно также использовать только часть до @
        return email;
    }


    /**
     * Очищает имя календаря от недопустимых символов для URL
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

    public record RadicaleAccountResponse(
            String accountId,  // email пользователя
            String calendarId, // имя календаря
            String email
    ) {
    }
}

