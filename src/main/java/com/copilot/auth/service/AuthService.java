package com.copilot.auth.service;

import com.copilot.auth.dto.request.ChangePasswordRequest;
import com.copilot.auth.dto.request.LoginRequest;
import com.copilot.auth.dto.request.RegisterRequest;
import com.copilot.auth.dto.request.ResetPasswordRequest;
import com.copilot.auth.dto.response.AuthResponse;
import com.copilot.auth.dto.response.LoginResponse;
import com.copilot.auth.dto.response.UserResponse;
import com.copilot.auth.model.User;
import com.copilot.auth.repository.UserRepository;
import com.copilot.dto.response.RegisterResponse;
import com.copilot.exception.UserAlreadyExistsException;
import com.copilot.security.JwtService;
import com.copilot.tools.calendar.CalendarProvisioningService;
import com.copilot.tools.email.EmailProvisioningService;
import com.copilot.tools.email.UserWelcomeEmailService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final EmailProvisioningService emailProvisioningService;
    private final CalendarProvisioningService calendarProvisioningService;
    private final UserWelcomeEmailService welcomeEmailService;

    @Transactional
    public RegisterResponse register(RegisterRequest dto) {
        log.info("Регистрация нового пользователя: {}", dto.email());
        
        if (repository.existsByEmail(dto.email())) {
            throw new UserAlreadyExistsException("Пользователь с email " + dto.email() + " уже существует");
        }

        // ВАЖНО: Проверяем, является ли это первым пользователем (админом)
        // Если в системе уже есть пользователи, регистрация через /register запрещена
        // Новые пользователи должны добавляться через импорт
        long userCount = repository.count();
        boolean isFirstUser = userCount == 0;
        
        if (!isFirstUser) {
            log.warn("Попытка регистрации через /register, но в системе уже есть пользователи ({}). " +
                    "Регистрация через /register доступна только для первого пользователя (админа). " +
                    "Новые пользователи должны добавляться через импорт.", userCount);
            throw new RuntimeException("Регистрация через /register доступна только для первого пользователя (админа). " +
                    "Новые пользователи должны добавляться через импорт пользователей.");
        }

        // Для первого пользователя (админа) создаем корпоративную почту и календарь
        EmailProvisioningService.MailSlurpInboxResponse corporateInbox = null;
        String emailProviderId = null;
        String emailPassword = null;
        String personalEmail = dto.email(); // Личная почта пользователя
        
        try {
            corporateInbox = emailProvisioningService.createCorporateEmail();
            emailProviderId = corporateInbox.id();
            emailPassword = generateTempPassword();
            log.info("Корпоративная почта создана для первого пользователя (админа): {}", corporateInbox.emailAddress());
        } catch (Exception e) {
            log.error("Не удалось создать корпоративную почту для первого пользователя (админа) {}: {}. " +
                    "Регистрация прервана.", dto.email(), e.getMessage(), e);
            throw new RuntimeException("Не удалось создать корпоративную почту для первого пользователя (админа): " + e.getMessage(), e);
        }

        // Создаем календарь в Radicale
        String calendarPassword = null;
        String calendarProviderId = null;
        try {
            calendarPassword = generateTempPassword();
            String emailForCalendar = corporateInbox.emailAddress();
            CalendarProvisioningService.RadicaleAccountResponse calendarAccount = 
                    calendarProvisioningService.createCalendar(emailForCalendar, calendarPassword, dto.firstName());
            calendarProviderId = calendarAccount.accountId();
            log.info("Календарь создан для первого пользователя (админа): {}", emailForCalendar);
        } catch (Exception e) {
            log.error("Не удалось создать календарь для первого пользователя (админа) {}: {}. " +
                    "Регистрация прервана.", dto.email(), e.getMessage(), e);
            throw new RuntimeException("Не удалось создать календарь для первого пользователя (админа): " + e.getMessage(), e);
        }

        // Создаем первого пользователя (админа) с ролью ADMIN
        // Используем корпоративную почту для входа
        String emailForLogin = corporateInbox.emailAddress();
        
        User user = User.builder()
                .email(emailForLogin) // Корпоративная почта для входа
                .personalEmail(personalEmail) // Личная почта пользователя
                .password(encoder.encode(dto.password()))
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .role("ADMIN") // Первый пользователь получает роль ADMIN
                .emailProviderId(emailProviderId) // ID MailSlurp inbox
                .emailPassword(encoder.encode(emailPassword)) // Зашифрованный пароль для корпоративной почты
                .calendarProviderId(calendarProviderId) // Email для календаря
                .calendarPassword(calendarPassword) // Пароль для календаря (НЕ зашифрован, нужен для Radicale)
                .isActive(true)
                .isPasswordChanged(false)
                .build();

        user = repository.save(user);
        log.info("Первый пользователь (админ) успешно зарегистрирован: {} (email для входа: {})", user.getId(), emailForLogin);

        // Отправляем письмо с данными для входа на корпоративную почту
        try {
            welcomeEmailService.sendSystemLoginEmail(
                    corporateInbox.emailAddress(),
                    emailProviderId,
                    dto.password(), // Пароль, который пользователь указал при регистрации
                    dto.firstName()
            );
            log.info("Письмо с данными для входа отправлено на корпоративную почту: {}", corporateInbox.emailAddress());
        } catch (Exception e) {
            log.error("Ошибка при отправке письма на корпоративную почту {}: {}", 
                    corporateInbox.emailAddress(), e.getMessage());
            // Не прерываем регистрацию, если не удалось отправить письмо
        }
        
        // Отправляем письмо на личную почту с данными для доступа к корпоративной почте и календарю
        try {
            welcomeEmailService.sendCorporateEmailAccessEmail(
                    personalEmail,
                    corporateInbox.emailAddress(),
                    emailPassword,
                    calendarProviderId,
                    calendarPassword,
                    dto.firstName()
            );
            log.info("Письмо с данными для доступа к корпоративной почте и календарю отправлено на личную почту: {}", personalEmail);
        } catch (Exception e) {
            log.error("Ошибка при отправке письма на личную почту {}: {}", 
                    personalEmail, e.getMessage());
            // Не прерываем регистрацию, если не удалось отправить письмо
        }

        String token = jwtService.generateAccessToken(
                user.getEmail(),
                user.getId().toString(),
                List.of(user.getRole())
        );
        String refreshToken = jwtService.generateRefreshToken(
                user.getEmail(),
                user.getId().toString()
        );

        return new RegisterResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                token,
                refreshToken
        );
    }
    
    /**
     * Генерирует временный пароль
     */
    private String generateTempPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder password = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 12; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        return password.toString();
    }

    @Transactional
    public LoginResponse login(LoginRequest dto) {
        log.info("Попытка входа пользователя: {}", dto.email());
        
        try {
            authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.email(), dto.password())
            );

            User user = repository.findByEmailAndDeletedAtIsNull(dto.email())
                    .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));

            if (!user.getIsActive()) {
                throw new BadCredentialsException("Аккаунт деактивирован");
            }

            user.setLastLoginAt(LocalDateTime.now());
            repository.save(user);

            String token = jwtService.generateAccessToken(
                    user.getEmail(),
                    user.getId().toString(),
                    List.of(user.getRole())
            );
            String refreshToken = jwtService.generateRefreshToken(
                    user.getEmail(),
                    user.getId().toString()
            );

            log.info("Пользователь успешно вошел: {}", user.getId());

            return new LoginResponse(
                    token,
                    refreshToken,
                    new LoginResponse.UserInfo(
                            user.getId(),
                            user.getEmail(),
                            user.getFirstName(),
                            user.getLastName(),
                            user.getRole()
                    )
            );
        } catch (Exception e) {
            log.error("Ошибка при входе пользователя {}: {}", dto.email(), e.getMessage());
            throw new BadCredentialsException("Неверный email или пароль", e);
        }
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        log.info("Обновление токена");
        
        try {
            var jws = jwtService.parse(refreshToken);
            Claims claims = jws.getPayload();

            if (!jwtService.isRefreshToken(claims)) {
                throw new BadCredentialsException("Неверный тип токена");
            }

            String email = claims.getSubject();

            User user = repository.findByEmailAndDeletedAtIsNull(email)
                    .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));

            if (!user.getIsActive()) {
                throw new BadCredentialsException("Аккаунт деактивирован");
            }

            String newToken = jwtService.generateAccessToken(
                    user.getEmail(),
                    user.getId().toString(),
                    List.of(user.getRole())
            );
            String newRefreshToken = jwtService.generateRefreshToken(
                    user.getEmail(),
                    user.getId().toString()
            );

            return new AuthResponse(newToken, newRefreshToken);
        } catch (Exception e) {
            log.error("Ошибка при обновлении токена: {}", e.getMessage());
            throw new BadCredentialsException("Неверный refresh token", e);
        }
    }

    @Transactional
    public void changePassword(ChangePasswordRequest dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("Смена пароля для пользователя: {}", email);

        User user = repository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));

        if (!encoder.matches(dto.oldPassword(), user.getPassword())) {
            throw new BadCredentialsException("Неверный текущий пароль");
        }

        user.setPassword(encoder.encode(dto.newPassword()));
        user.setIsPasswordChanged(true);
        repository.save(user);

        log.info("Пароль успешно изменен для пользователя: {}", email);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest dto) {
        log.info("Сброс пароля по токену");
        
        // TODO: Реализовать проверку токена из user_invitations таблицы
        // Пока что это заглушка - нужно будет добавить UserInvitation entity и repository
        throw new UnsupportedOperationException("Сброс пароля будет реализован после добавления UserInvitation");
    }

    public UserResponse getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("Получение информации о текущем пользователе: {}", email);

        User user = repository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDepartment(),
                user.getRole(),
                user.getIsActive()
        );
    }
}

