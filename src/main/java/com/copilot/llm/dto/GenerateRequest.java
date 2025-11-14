package com.copilot.llm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO для запроса генерации текста через LLM
 */
public record GenerateRequest(
        @NotBlank(message = "Промпт обязателен")
        @Size(max = 10000, message = "Промпт не должен превышать 10000 символов")
        String prompt
) {
}

