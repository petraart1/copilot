package com.copilot.tools.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserWelcomeEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:assistant@company.com}")
    private String fromEmail;

    /**
     * Отправляет письмо на корпоративную почту с данными для входа в систему
     */
    public void sendSystemLoginEmail(String corporateEmail, String inboxId, String tempPassword, String firstName) {
        log.info("Отправка письма с данными для входа на корпоративную почту: {}", corporateEmail);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(corporateEmail);
            message.setSubject("Добро пожаловать в Business Assistant!");

            String emailBody = buildSystemLoginEmailBody(firstName, corporateEmail, tempPassword);
            message.setText(emailBody);

            mailSender.send(message);
            log.info("Письмо с данными для входа отправлено на корпоративную почту: {}", corporateEmail);

        } catch (Exception e) {
            log.error("Ошибка при отправке письма на корпоративную почту {}: {}", 
                    corporateEmail, e.getMessage(), e);
            throw new RuntimeException("Не удалось отправить письмо на корпоративную почту: " + e.getMessage(), e);
        }
    }

    /**
     * Отправляет письмо на личную почту с данными для доступа к корпоративной почте и календарю
     */
    public void sendCorporateEmailAccessEmail(String personalEmail, String corporateEmail, 
                                             String emailPassword, String calendarEmail, 
                                             String calendarPassword, String firstName) {
        log.info("Отправка письма с данными для доступа к корпоративной почте на личную почту: {}", personalEmail);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(personalEmail);
            message.setSubject("Доступ к корпоративной почте Business Assistant");

            String emailBody = buildCorporateEmailAccessEmailBody(firstName, corporateEmail, personalEmail, 
                    emailPassword, calendarEmail, calendarPassword);
            message.setText(emailBody);

            mailSender.send(message);
            log.info("Письмо с данными для доступа к корпоративной почте отправлено на: {}", personalEmail);

        } catch (Exception e) {
            log.error("Ошибка при отправке письма на личную почту {}: {}", 
                    personalEmail, e.getMessage(), e);
            throw new RuntimeException("Не удалось отправить письмо на личную почту: " + e.getMessage(), e);
        }
    }

    private String buildSystemLoginEmailBody(String firstName, String corporateEmail, String tempPassword) {
        String name = firstName != null && !firstName.isEmpty() ? firstName : "Коллега";
        
        return String.format("""
                Здравствуйте, %s!
                
                Вы были добавлены в систему Business Assistant.
                
                Данные для входа в систему:
                Email для входа: %s
                Временный пароль: %s
                
                ВАЖНО: При первом входе вам необходимо сменить пароль.
                
                С уважением,
                Команда Business Assistant
                """, name, corporateEmail, tempPassword);
    }

    private String buildCorporateEmailAccessEmailBody(String firstName, String corporateEmail, 
                                                     String personalEmail, String emailPassword,
                                                     String calendarEmail, String calendarPassword) {
        String name = firstName != null && !firstName.isEmpty() ? firstName : "Коллега";

        String fullCalendarUrl = buildFullCalendarUrl(calendarEmail, firstName);

        String username = calendarEmail;
        String calendarName = (firstName != null && !firstName.isEmpty() ? firstName : "Work") + "'s Calendar";
        String calendarNameSanitized = calendarName.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-");
        
        return String.format("""
                Здравствуйте, %s!
                
                Вам была создана корпоративная почта и календарь в системе Business Assistant.
                
                Данные для доступа к корпоративной почте (MailSlurp):
                Email: %s
                Пароль: %s
                IMAP сервер: imap.mailslurp.com
                IMAP порт: 993 (SSL)
                
                Данные для доступа к календарю (Radicale/CalDAV):
                CalDAV URL: %s
                Email (Username): %s
                Пароль: %s
                
                Для подключения календаря к вашему календарному приложению (например, Thunderbird, Apple Calendar, Google Calendar):
                1. Добавьте новый CalDAV календарь
                2. Укажите URL: %s
                3. Используйте Username: %s
                4. Используйте пароль: %s
                
                Примечание: Если календарь развернут локально (через Docker), используйте внутренний адрес:
                http://calendar:5232/%s/%s/
                
                Для внешнего доступа настройте прокси или используйте публичный URL календаря.
                
                Видеовстречи (Jitsi Meet):
                Для участия в видеовстречах используйте сервис Jitsi Meet.
                Ссылки на встречи будут автоматически добавляться в события календаря.
                Регистрация не требуется - просто перейдите по ссылке из календаря.
                Сервис доступен по адресу: https://meet.jit.si
                
                С уважением,
                Команда Business Assistant
                """, name, corporateEmail, emailPassword, fullCalendarUrl, calendarEmail, calendarPassword, 
                fullCalendarUrl, calendarEmail, calendarPassword, username, calendarNameSanitized);
    }
    
    @Value("${calendar.caldav.base-url:http://calendar:5232}")
    private String calendarBaseUrl;
    
    private String getCalendarUrl() {
        return calendarBaseUrl;
    }

    private String buildFullCalendarUrl(String calendarEmail, String firstName) {
        String calendarUrl = getCalendarUrl();
        String username = calendarEmail.contains("@") ? calendarEmail : calendarEmail;
        String calendarName = (firstName != null && !firstName.isEmpty() ? firstName : "Work") + "'s Calendar";
        calendarName = calendarName.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-");
        return calendarUrl + "/" + username + "/" + calendarName + "/";
    }
}

