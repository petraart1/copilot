package com.copilot.chat.controller;

import com.copilot.auth.repository.UserRepository;
import com.copilot.chat.dto.request.CreateChatRequest;
import com.copilot.chat.dto.response.ChatResponse;
import com.copilot.chat.service.ChatService;
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
@RequestMapping("/chats")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "API для работы с чатами")
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    @Operation(
            summary = "Создать новый чат",
            description = "Создает новый чат для текущего пользователя"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Чат успешно создан"),
            @ApiResponse(responseCode = "400", description = "Неверные данные запроса"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @PostMapping
    public ResponseEntity<ChatResponse> createChat(
            @Valid @RequestBody CreateChatRequest request,
            Authentication authentication) {
        UUID userId = getCurrentUserId(authentication);
        log.info("Создание чата пользователем: {}", userId);

        ChatResponse response = chatService.createChat(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Получить список чатов",
            description = "Возвращает список чатов текущего пользователя с пагинацией"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Список чатов успешно получен"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @GetMapping
    public ResponseEntity<Page<ChatResponse>> getChats(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        UUID userId = getCurrentUserId(authentication);
        log.debug("Получение чатов пользователя: {}", userId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<ChatResponse> chats = chatService.getUserChats(userId, pageable);
        return ResponseEntity.ok(chats);
    }

    @Operation(
            summary = "Получить чат по ID",
            description = "Возвращает информацию о конкретном чате"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Чат успешно получен"),
            @ApiResponse(responseCode = "404", description = "Чат не найден"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @GetMapping("/{chatId}")
    public ResponseEntity<ChatResponse> getChat(
            @PathVariable UUID chatId,
            Authentication authentication) {
        UUID userId = getCurrentUserId(authentication);
        log.debug("Получение чата {} пользователем {}", chatId, userId);

        ChatResponse response = chatService.getChatById(chatId, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Архивировать чат",
            description = "Перемещает чат в архив"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Чат успешно архивирован"),
            @ApiResponse(responseCode = "404", description = "Чат не найден"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @PostMapping("/{chatId}/archive")
    public ResponseEntity<Void> archiveChat(
            @PathVariable UUID chatId,
            Authentication authentication) {
        UUID userId = getCurrentUserId(authentication);
        log.info("Архивирование чата {} пользователем {}", chatId, userId);

        chatService.archiveChat(chatId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Удалить чат",
            description = "Удаляет чат (soft delete)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Чат успешно удален"),
            @ApiResponse(responseCode = "404", description = "Чат не найден"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> deleteChat(
            @PathVariable UUID chatId,
            Authentication authentication) {
        UUID userId = getCurrentUserId(authentication);
        log.info("Удаление чата {} пользователем {}", chatId, userId);

        chatService.deleteChat(chatId, userId);
        return ResponseEntity.noContent().build();
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
}
