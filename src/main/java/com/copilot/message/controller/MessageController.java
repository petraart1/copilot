package com.copilot.message.controller;

import com.copilot.auth.repository.UserRepository;
import com.copilot.message.dto.request.SendMessageRequest;
import com.copilot.message.dto.response.MessageResponse;
import com.copilot.message.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/chats/{chatId}/messages")
@RequiredArgsConstructor
@Tag(name = "Message", description = "API для работы с сообщениями")
public class MessageController {

    private final MessageService messageService;
    private final UserRepository userRepository;

    @Operation(
            summary = "Отправить сообщение в чат",
            description = "Отправляет сообщение в чат и получает ответ от AI-агента"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Сообщение успешно отправлено"),
            @ApiResponse(responseCode = "400", description = "Неверные данные запроса"),
            @ApiResponse(responseCode = "404", description = "Чат не найден"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable UUID chatId,
            @Valid @RequestBody SendMessageRequest request,
            Authentication authentication) {
        UUID userId = getCurrentUserId(authentication);
        String userEmail = getCurrentUserEmail(authentication);
        log.info("Отправка сообщения в чат {} пользователем {}", chatId, userId);

        MessageResponse response = messageService.sendMessage(chatId, userId, userEmail, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Получить историю сообщений",
            description = "Возвращает историю сообщений чата с пагинацией"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "История сообщений успешно получена"),
            @ApiResponse(responseCode = "404", description = "Чат не найден"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @GetMapping
    public ResponseEntity<Page<MessageResponse>> getMessages(
            @PathVariable UUID chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            Authentication authentication) {
        UUID userId = getCurrentUserId(authentication);
        log.debug("Получение сообщений чата {} пользователем {}", chatId, userId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MessageResponse> messages = messageService.getChatMessages(chatId, userId, pageable);
        return ResponseEntity.ok(messages);
    }

    private UUID getCurrentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("Пользователь не авторизован");
        }

        Object principal = authentication.getPrincipal();
        String email;
        
        if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            // Для @WithMockUser principal - это строка (username)
            email = principal.toString();
        }
        
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));
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

