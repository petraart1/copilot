package com.copilot.dto.response;

import java.util.UUID;

public record RegisterResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String token,
        String refreshToken
) {
}

