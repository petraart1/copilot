package com.copilot.health.service;

import com.copilot.health.dto.ServiceHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Проверка состояния Redis
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisHealthChecker {

    private final RedisConnectionFactory redisConnectionFactory;

    public ServiceHealth check() {
        long startTime = System.currentTimeMillis();
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.ping();
            long responseTime = System.currentTimeMillis() - startTime;
            return ServiceHealth.up("Redis доступен", responseTime);
        } catch (Exception e) {
            log.error("Ошибка при проверке Redis: {}", e.getMessage());
            return ServiceHealth.down("Redis недоступен: " + e.getMessage());
        }
    }
}

