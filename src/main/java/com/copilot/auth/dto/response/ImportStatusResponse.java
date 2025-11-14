package com.copilot.auth.dto.response;

import java.util.List;
import java.util.UUID;

public record ImportStatusResponse(
        UUID importLogId,
        String status,
        Integer totalRecords,
        Integer processedRecords,
        Integer errorCount,
        List<String> errors
) {
}

