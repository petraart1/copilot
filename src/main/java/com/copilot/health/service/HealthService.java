package com.copilot.health.service;

import com.copilot.health.dto.HealthResponse;
import com.copilot.health.dto.ServiceHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для проверки состояния всех зависимых сервисов
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthService {

    private final DatabaseHealthChecker databaseHealthChecker;
    private final RedisHealthChecker redisHealthChecker;
    private final MailSlurpHealthChecker mailSlurpHealthChecker;
    private final RadicaleHealthChecker radicaleHealthChecker;
    private final LLMHealthChecker llmHealthChecker;

    /**
     * Проверяет состояние всех сервисов
     */
    public HealthResponse checkAll() {
        Map<String, ServiceHealth> services = new HashMap<>();

        // Проверка БД
        services.put("database", databaseHealthChecker.check());

        // Проверка Redis
        services.put("redis", redisHealthChecker.check());

        // Проверка MailSlurp
        services.put("mailslurp", mailSlurpHealthChecker.check());

        // Проверка Radicale
        services.put("radicale", radicaleHealthChecker.check());

        // Проверка LLM
        services.put("llm", llmHealthChecker.check());

        // Определяем общий статус
        boolean allHealthy = services.values().stream()
                .allMatch(health -> "UP".equals(health.status()));

        String overallStatus = allHealthy ? "UP" : "DOWN";

        return new HealthResponse(overallStatus, services);
    }
}

