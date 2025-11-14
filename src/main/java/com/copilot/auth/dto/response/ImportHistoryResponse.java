package com.copilot.auth.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ImportHistoryResponse(
        List<ImportInfo> imports
) {
    public record ImportInfo(
            UUID importLogId,
            String fileName,
            Integer totalRecords,
            Integer successfulRecords,
            Integer failedRecords,
            String status,
            LocalDateTime createdAt
    ) {
    }
}

