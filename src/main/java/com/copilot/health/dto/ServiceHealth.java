package com.copilot.health.dto;

/**
 * DTO для состояния отдельного сервиса
 */
public record ServiceHealth(
        String status,
        String message,
        Long responseTimeMs
) {
    public static ServiceHealth up(String message, Long responseTimeMs) {
        return new ServiceHealth("UP", message, responseTimeMs);
    }

    public static ServiceHealth down(String message) {
        return new ServiceHealth("DOWN", message, null);
    }
}

