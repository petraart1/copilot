package com.copilot.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExecuteTaskRequest(
        @NotBlank(message = "Задача обязательна")
        @Size(max = 5000, message = "Задача не должна превышать 5000 символов")
        String task
) {
}

