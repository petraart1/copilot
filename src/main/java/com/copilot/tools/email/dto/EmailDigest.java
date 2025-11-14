package com.copilot.tools.email.dto;

import java.time.LocalDateTime;

public record EmailDigest(
        String summary,
        int emailCount,
        LocalDateTime generatedAt,
        LocalDateTime lastEmailAt
) {
}

