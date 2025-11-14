package com.copilot.tools.email.controller;

import com.copilot.tools.email.EmailDigestService;
import com.copilot.tools.email.EmailReadService;
import com.copilot.tools.email.dto.EmailDigest;
import com.copilot.auth.model.User;
import com.copilot.auth.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/email")
@RequiredArgsConstructor
@Tag(name = "Email", description = "API для работы с email")
public class EmailController {

    private final EmailDigestService emailDigestService;
    private final EmailReadService emailReadService;
    private final UserRepository userRepository;

    @Operation(
            summary = "Получить email digest (сводку писем)",
            description = "Возвращает сводку новых писем для текущего пользователя. " +
                    "Если digest нет в кэше, запускает чтение писем и создание digest."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Digest успешно получен"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    })
    @GetMapping("/digest")
    public ResponseEntity<EmailDigest> getEmailDigest(Authentication authentication) {
        String email = getCurrentUserEmail(authentication);
        log.info("Запрос digest для пользователя: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        EmailDigest digest = emailDigestService.getCachedDigest(user.getId());
        
        if (digest == null) {
            log.info("Digest нет в кэше, создаем новый для пользователя: {}", email);
            emailReadService.readAndDigestEmailsForUser(user);
            digest = emailDigestService.getCachedDigest(user.getId());
            
            if (digest == null) {
                digest = new EmailDigest(
                        "Письма еще обрабатываются. Попробуйте позже.",
                        0,
                        null,
                        null
                );
            }
        }

        return ResponseEntity.ok(digest);
    }

    @Operation(
            summary = "Принудительно обновить email digest",
            description = "Принудительно читает новые письма и создает свежий digest для текущего пользователя"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Digest успешно обновлен"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @PostMapping("/digest/refresh")
    public ResponseEntity<EmailDigest> refreshEmailDigest(Authentication authentication) {
        String email = getCurrentUserEmail(authentication);
        log.info("Принудительное обновление digest для пользователя: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));

        emailReadService.readAndDigestEmailsForUser(user);
        
        EmailDigest digest = emailDigestService.getCachedDigest(user.getId());
        if (digest == null) {
            digest = new EmailDigest(
                    "Письма обрабатываются",
                    0,
                    LocalDateTime.now(),
                    null
            );
        }

        return ResponseEntity.ok(digest);
    }

    private String getCurrentUserEmail(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("Пользователь не авторизован");
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        
        return principal.toString();
    }
}

