package com.copilot.message.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO для отправки сообщения в чат
 */
public record SendMessageRequest(
        @NotBlank(message = "Сообщение не может быть пустым")
        @Size(max = 10000, message = "Сообщение не должно превышать 10000 символов")
        String message
) {
}


