package com.copilot.health.dto;

import java.util.Map;

/**
 * DTO для ответа health check
 */
public record HealthResponse(
        String status,
        Map<String, ServiceHealth> services
) {
    public boolean isHealthy() {
        return "UP".equals(status);
    }
}

