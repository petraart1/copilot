package com.copilot.message.dto.response;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO для ответа с информацией о сообщении
 */
public record MessageResponse(
        UUID id,
        String userMessage,
        String aiResponse,
        String responseType,
        Integer promptTokens,
        Integer completionTokens,
        Map<String, Object> contextData,
        LocalDateTime createdAt
) {
}


