package com.copilot.llm.tools;

import java.util.List;
import java.util.Map;

public class ToolDefinitions {

    public static List<Map<String, Object>> definitions() {
        return List.of(
                // Встреча
                Map.of(
                        "name", "schedule_meeting",
                        "description", "Запланировать встречу: создать в Zoom/Jitsi, добавить в календарь, отправить уведомления",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "title", Map.of("type", "string", "description", "Название встречи"),
                                        "start_time", Map.of("type", "string", "format", "date-time", "description", "Начало встречи (ISO 8601)"),
                                        "duration_minutes", Map.of("type", "integer", "default", 60),
                                        "attendees", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Email адреса участников"),
                                        "description", Map.of("type", "string"),
                                        "conference_provider", Map.of("type", "string", "enum", List.of("jitsi", "zoom", "whereby"), "default", "jitsi")
                                ),
                                "required", List.of("title", "start_time", "attendees")
                        )
                ),
                // Email уведомления
                Map.of(
                        "name", "send_notification",
                        "description", "Отправить email уведомление одному или нескольким адресатам",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "recipients", Map.of("type", "array", "items", Map.of("type", "string")),
                                        "subject", Map.of("type", "string"),
                                        "message", Map.of("type", "string")
                                ),
                                "required", List.of("recipients", "subject", "message")
                        )
                ),
                // Письмо
                Map.of(
                        "name", "compose_letter",
                        "description", "Написать письмо",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "recipient", Map.of("type", "string"),
                                        "subject", Map.of("type", "string"),
                                        "content", Map.of("type", "string")
                                ),
                                "required", List.of("recipient", "subject", "content")
                        )
                )
        );
    }
}

