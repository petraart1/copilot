package com.copilot.chat.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ChatResponse(
        UUID id,
        String title,
        Boolean isArchived,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long messageCount
) {
}


