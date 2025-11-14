package com.copilot.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserImportRow(
        @NotBlank(message = "Email обязателен")
        @Email(message = "Некорректный формат email")
        String email,
        
        String firstName,
        
        String lastName,
        
        String phone,
        
        String telegram,
        
        String department,
        
        String role
) {
}

