package com.copilot.auth.dto.response;

import java.util.UUID;

public record LoginResponse(
        String token,
        String refreshToken,
        UserInfo user
) {
    public record UserInfo(
            UUID id,
            String email,
            String firstName,
            String lastName,
            String role
    ) {
    }
}

