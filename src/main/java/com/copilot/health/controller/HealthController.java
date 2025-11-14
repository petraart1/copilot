package com.copilot.health.controller;

import com.copilot.health.dto.HealthResponse;
import com.copilot.health.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "API для проверки состояния сервисов")
public class HealthController {

    private final HealthService healthService;

    @Operation(
            summary = "Проверка состояния всех сервисов",
            description = "Возвращает статус всех зависимых сервисов: БД, Redis, MailSlurp, Radicale, LLM"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Проверка выполнена"),
            @ApiResponse(responseCode = "503", description = "Один или несколько сервисов недоступны")
    })
    @GetMapping
    public ResponseEntity<HealthResponse> checkHealth() {
        log.debug("Проверка состояния сервисов");
        HealthResponse health = healthService.checkAll();
        
        if (health.isHealthy()) {
            return ResponseEntity.ok(health);
        } else {
            return ResponseEntity.status(503).body(health);
        }
    }
}

