package com.copilot.auth.service;

import com.copilot.auth.dto.request.UserImportRow;
import com.copilot.auth.model.ImportLog;
import com.copilot.auth.model.User;
import com.copilot.auth.repository.ImportLogRepository;
import com.copilot.auth.repository.UserRepository;
import com.copilot.tools.email.EmailProvisioningService;
import com.copilot.tools.email.UserWelcomeEmailService;
import com.copilot.tools.calendar.CalendarProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserImportService {

    private final UserRepository userRepository;
    private final ImportLogRepository importLogRepository;
    private final UserImportParser parser;
    private final PasswordEncoder passwordEncoder;
    private final EmailProvisioningService emailProvisioningService;
    private final UserWelcomeEmailService welcomeEmailService;
    private final CalendarProvisioningService calendarProvisioningService;

    @Transactional
    public ImportLog startImport(MultipartFile file, UUID importedByUserId) {
        log.info("Начало импорта пользователей. Файл: {}, Импортировал: {}", 
                file.getOriginalFilename(), importedByUserId);

        ImportLog importLog = ImportLog.builder()
                .importedByUserId(importedByUserId)
                .fileName(file.getOriginalFilename())
                .fileSizeBytes(file.getSize())
                .status("PROCESSING")
                .totalRecords(0)
                .successfulRecords(0)
                .failedRecords(0)
                .build();

        importLog = importLogRepository.save(importLog);

        processImportAsync(file, importLog.getId());

        return importLog;
    }

    @Async
    public void processImportAsync(MultipartFile file, UUID importLogId) {
        try {
            processImport(file, importLogId);
        } catch (Exception e) {
            log.error("Ошибка при асинхронной обработке импорта {}: {}", importLogId, e.getMessage(), e);
            updateImportLogStatus(importLogId, "FAILED", null, null, 
                    "Критическая ошибка: " + e.getMessage());
        }
    }

    @Transactional
    public void processImport(MultipartFile file, UUID importLogId) {
        ImportLog importLog = importLogRepository.findById(importLogId)
                .orElseThrow(() -> new IllegalArgumentException("ImportLog не найден: " + importLogId));

        List<UserImportRow> rows;
        try {
            rows = parser.parseFile(file);
        } catch (Exception e) {
            log.error("Ошибка при парсинге файла: {}", e.getMessage());
            updateImportLogStatus(importLogId, "FAILED", 0, 0, 
                    "Ошибка парсинга файла: " + e.getMessage());
            return;
        }

        importLog.setTotalRecords(rows.size());
        importLogRepository.save(importLog);

        List<String> errors = new ArrayList<>();
        int successful = 0;
        int failed = 0;

        for (UserImportRow row : rows) {
            try {
                importUser(row);
                successful++;
            } catch (Exception e) {
                failed++;
                String error = String.format("%s: %s", row.email(), e.getMessage());
                errors.add(error);
                log.warn("Ошибка при импорте пользователя {}: {}", row.email(), e.getMessage());
            }
        }

        String status = failed == 0 ? "COMPLETED" : 
                       (successful == 0 ? "FAILED" : "PARTIALLY_FAILED");

        String errorDetails = errors.isEmpty() ? null : String.join("\n", errors);
        updateImportLogStatus(importLogId, status, successful, failed, errorDetails);

        log.info("Импорт завершен. Успешно: {}, Ошибок: {}", successful, failed);
    }

    private void importUser(UserImportRow row) {
        EmailProvisioningService.MailSlurpInboxResponse corporateInbox;
        try {
            corporateInbox = emailProvisioningService.createCorporateEmail();
            log.info("Корпоративная почта создана: {}", corporateInbox.emailAddress());
        } catch (Exception e) {
            log.error("Ошибка при создании корпоративной почты: {}", e.getMessage());
            throw new RuntimeException("Не удалось создать корпоративную почту: " + e.getMessage(), e);
        }

        if (userRepository.existsByEmail(corporateInbox.emailAddress())) {
            throw new IllegalArgumentException("Пользователь с email " + corporateInbox.emailAddress() + " уже существует");
        }

        // Генерируем временный пароль для входа в систему
        String tempPassword = generateTempPassword();
        String encodedPassword = passwordEncoder.encode(tempPassword);

        // Генерируем пароль для доступа к корпоративной почте (для IMAP)
        String emailPassword = generateTempPassword();
        String encodedEmailPassword = passwordEncoder.encode(emailPassword);

        // Создаем календарь в Radicale
        // ВАЖНО: Пароль календаря НЕ шифруется через BCrypt, так как он нужен для аутентификации в Radicale
        // BCrypt - это одностороннее хеширование, его нельзя расшифровать
        // Для безопасности можно использовать Jasypt, но пока храним в открытом виде (Radicale - внутренний сервис)
        String calendarPassword = generateTempPassword();
        // НЕ шифруем пароль календаря - он нужен для аутентификации в Radicale
        // TODO: В будущем использовать Jasypt для шифрования паролей календаря
        CalendarProvisioningService.RadicaleAccountResponse calendarAccount;
        try {
            calendarAccount = calendarProvisioningService.createCalendar(
                    corporateInbox.emailAddress(), // Используем корпоративную почту для календаря
                    calendarPassword, // Исходный пароль (не зашифрованный)
                    row.firstName()
            );
            log.info("Календарь создан для пользователя: {}", corporateInbox.emailAddress());
        } catch (Exception e) {
            log.error("Ошибка при создании календаря для {}: {}", corporateInbox.emailAddress(), e.getMessage(), e);
            // Не прерываем импорт, если не удалось создать календарь
            calendarAccount = new CalendarProvisioningService.RadicaleAccountResponse(
                    corporateInbox.emailAddress(), "default", corporateInbox.emailAddress());
        }

        // Создаем пользователя с корпоративной почтой для входа
        User user = User.builder()
                .email(corporateInbox.emailAddress()) // Корпоративная почта - используется для входа в систему
                .personalEmail(row.email()) // Личная почта из файла
                .password(encodedPassword)
                .firstName(row.firstName())
                .lastName(row.lastName())
                .phone(row.phone())
                .telegram(row.telegram())
                .department(row.department())
                .role(row.role() != null ? row.role() : "EMPLOYEE")
                .emailProviderId(corporateInbox.id()) // ID MailSlurp inbox
                .emailPassword(encodedEmailPassword) // Зашифрованный пароль для доступа к корпоративной почте
                .calendarProviderId(calendarAccount.accountId()) // Email пользователя для Radicale
                .calendarPassword(calendarPassword) // Пароль для доступа к календарю (НЕ зашифрован, нужен для Radicale)
                .isActive(true)
                .isPasswordChanged(false) // Пользователь должен сменить пароль при первом входе
                .build();

        user = userRepository.save(user);

        // Отправляем письмо на корпоративную почту с данными для входа в систему
        try {
            welcomeEmailService.sendSystemLoginEmail(
                    corporateInbox.emailAddress(), // Корпоративная почта
                    corporateInbox.id(), // MailSlurp inbox ID
                    tempPassword, // Временный пароль для входа в систему
                    row.firstName()
            );
            log.info("Письмо с данными для входа отправлено на корпоративную почту: {}", corporateInbox.emailAddress());
        } catch (Exception e) {
            log.error("Ошибка при отправке письма на корпоративную почту {}: {}", 
                    corporateInbox.emailAddress(), e.getMessage());
            // Не прерываем импорт, если не удалось отправить письмо
        }

        // Отправляем письмо на личную почту с данными для доступа к корпоративной почте и календарю
        try {
            welcomeEmailService.sendCorporateEmailAccessEmail(
                    row.email(), // Личная почта
                    corporateInbox.emailAddress(), // Корпоративная почта
                    emailPassword, // Пароль для доступа к корпоративной почте
                    calendarAccount.email(), // Email для доступа к календарю
                    calendarPassword, // Пароль для доступа к календарю
                    row.firstName()
            );
            log.info("Письмо с данными для доступа к корпоративной почте и календарю отправлено на личную почту: {}", row.email());
        } catch (Exception e) {
            log.error("Ошибка при отправке письма на личную почту {}: {}", 
                    row.email(), e.getMessage());
            // Не прерываем импорт, если не удалось отправить письмо
        }

        log.info("Пользователь импортирован: корпоративная почта (для входа): {}, личная почта: {}", 
                user.getEmail(), user.getPersonalEmail());
    }

    private String generateTempPassword() {
        // Генерируем случайный пароль из 12 символов
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder password = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 12; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        return password.toString();
    }

    @Transactional
    public void updateImportLogStatus(UUID importLogId, String status, 
                                     Integer successful, Integer failed, String errorDetails) {
        ImportLog importLog = importLogRepository.findById(importLogId)
                .orElseThrow(() -> new IllegalArgumentException("ImportLog не найден: " + importLogId));

        importLog.setStatus(status);
        if (successful != null) {
            importLog.setSuccessfulRecords(successful);
        }
        if (failed != null) {
            importLog.setFailedRecords(failed);
        }
        if (errorDetails != null) {
            importLog.setErrorDetails(errorDetails);
        }
        importLog.setCompletedAt(LocalDateTime.now());

        importLogRepository.save(importLog);
    }

    public ImportLog getImportStatus(UUID importLogId) {
        return importLogRepository.findById(importLogId)
                .orElseThrow(() -> new IllegalArgumentException("ImportLog не найден: " + importLogId));
    }

    public List<ImportLog> getImportHistory(UUID userId) {
        return importLogRepository.findImportHistoryByUserId(userId);
    }
}

