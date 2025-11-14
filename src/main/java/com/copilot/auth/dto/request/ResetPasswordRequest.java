package com.copilot.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Токен сброса обязателен")
        String resetToken,
        
        @NotBlank(message = "Новый пароль обязателен")
        @Size(min = 6, message = "Пароль должен содержать минимум 6 символов")
        String newPassword
) {
}

