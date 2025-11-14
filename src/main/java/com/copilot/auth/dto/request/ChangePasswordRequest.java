package com.copilot.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Текущий пароль обязателен")
        String oldPassword,
        
        @NotBlank(message = "Новый пароль обязателен")
        @Size(min = 6, message = "Пароль должен содержать минимум 6 символов")
        String newPassword
) {
}

