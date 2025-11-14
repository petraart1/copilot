package com.copilot.tools.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record CreateEventRequest(
        @NotBlank(message = "Название события обязательно")
        @Size(max = 255, message = "Название события не должно превышать 255 символов")
        String title,

        @NotNull(message = "Дата и время начала обязательны")
        LocalDateTime startTime,

        @NotNull(message = "Длительность обязательна")
        Integer durationMinutes,

        String description,

        @NotNull(message = "Список участников обязателен")
        @Size(min = 1, message = "Должен быть хотя бы один участник")
        List<String> attendeeEmails,

        String location
) {
}

