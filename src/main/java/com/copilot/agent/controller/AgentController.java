package com.copilot.agent.controller;

import com.copilot.agent.dto.request.ExecuteTaskRequest;
import com.copilot.agent.dto.response.AgentActionResponse;
import com.copilot.agent.dto.response.ExecuteTaskResponse;
import com.copilot.agent.service.AgentHistoryService;
import com.copilot.auth.repository.UserRepository;
import com.copilot.llm.service.AgentService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Tag(name = "Agent", description = "API для работы с AI-агентом")
public class AgentController {

    private final AgentService agentService;
    private final AgentHistoryService agentHistoryService;
    private final UserRepository userRepository;

    @Operation(
            summary = "Выполнить задачу через AI-агента",
            description = "Агент анализирует запрос пользователя и выполняет необходимые действия через доступные инструменты " +
                    "(планирование встреч, отправка уведомлений и т.д.). Максимум 5 итераций."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Задача выполнена успешно"),
            @ApiResponse(responseCode = "400", description = "Неверные данные запроса"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @PostMapping("/execute")
    public ResponseEntity<ExecuteTaskResponse> executeTask(
            @Valid @RequestBody ExecuteTaskRequest request,
            Authentication authentication) {
        String userEmail = getCurrentUserEmail(authentication);
        log.info("Запрос на выполнение задачи от пользователя {}: {}", userEmail, request.task());

        ExecuteTaskResponse response = agentService.executeTask(request.task(), userEmail);
        return ResponseEntity.ok(response);
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

    @Operation(
            summary = "Получить историю действий агента",
            description = "Возвращает историю действий агента для текущего пользователя с возможностью фильтрации"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "История действий успешно получена"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @GetMapping("/history")
    public ResponseEntity<Page<AgentActionResponse>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            Authentication authentication) {
        UUID userId = getCurrentUserId(authentication);
        log.debug("Получение истории действий агента для пользователя: {}", userId);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AgentActionResponse> history;
        if (status != null || actionType != null || from != null || to != null) {
            history = agentHistoryService.getUserHistoryWithFilters(userId, status, actionType, from, to, pageable);
        } else {
            history = agentHistoryService.getUserHistory(userId, pageable);
        }

        return ResponseEntity.ok(history);
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
            email = principal.toString();
        }
        
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .map(user -> user.getId())
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + email));
    }
}
