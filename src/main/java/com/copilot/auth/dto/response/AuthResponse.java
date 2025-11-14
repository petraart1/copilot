package com.copilot.auth.dto.response;

public record AuthResponse(
        String token,
        String refreshToken
) {
}

