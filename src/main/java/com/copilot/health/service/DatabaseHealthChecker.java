package com.copilot.health.service;

import com.copilot.health.dto.ServiceHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Проверка состояния базы данных
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseHealthChecker {

    private final JdbcTemplate jdbcTemplate;

    public ServiceHealth check() {
        long startTime = System.currentTimeMillis();
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long responseTime = System.currentTimeMillis() - startTime;
            return ServiceHealth.up("База данных доступна", responseTime);
        } catch (Exception e) {
            log.error("Ошибка при проверке БД: {}", e.getMessage());
            return ServiceHealth.down("База данных недоступна: " + e.getMessage());
        }
    }
}

