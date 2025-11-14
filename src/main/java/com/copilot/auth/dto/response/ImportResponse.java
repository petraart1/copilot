package com.copilot.auth.dto.response;

import java.util.UUID;

public record ImportResponse(
        UUID importLogId,
        String status,
        Integer totalRecords,
        Integer processedRecords,
        Integer errorCount
) {
}

