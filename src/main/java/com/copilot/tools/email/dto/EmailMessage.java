package com.copilot.tools.email.dto;

import java.time.LocalDateTime;

public record EmailMessage(
        String id,
        String from,
        String to,
        String subject,
        String body,
        LocalDateTime receivedAt
) {
}

