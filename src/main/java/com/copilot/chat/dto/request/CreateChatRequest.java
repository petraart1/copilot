package com.copilot.chat.dto.request;

import jakarta.validation.constraints.Size;

public record CreateChatRequest(
        @Size(max = 255, message = "Название чата не должно превышать 255 символов")
        String title
) {
}


