package com.copilot.auth.dto.response;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String department,
        String role,
        Boolean isActive
) {
}

