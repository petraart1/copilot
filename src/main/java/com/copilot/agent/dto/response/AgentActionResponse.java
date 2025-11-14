package com.copilot.agent.dto.response;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record AgentActionResponse(
        UUID id,
        UUID chatId,
        String actionType,
        Map<String, Object> inputData,
        Map<String, Object> outputData,
        String status,
        String errorMessage,
        Integer durationMs,
        LocalDateTime createdAt
) {
}

