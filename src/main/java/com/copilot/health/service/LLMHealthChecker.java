package com.copilot.health.service;

import com.copilot.health.dto.ServiceHealth;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Проверка состояния LLM (OpenRouter/Gemini)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LLMHealthChecker {

    private final ChatModel chatModel;

    public ServiceHealth check() {
        long startTime = System.currentTimeMillis();
        try {
            // Простой запрос для проверки доступности LLM
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from("Ты помощник. Отвечай кратко."),
                            UserMessage.from("ping")
                    ))
                    .build();

            ChatResponse response = chatModel.chat(request);
            String text = response.aiMessage().text();

            long responseTime = System.currentTimeMillis() - startTime;

            if (text != null && !text.isEmpty()) {
                return ServiceHealth.up("LLM доступен", responseTime);
            } else {
                return ServiceHealth.down("LLM вернул пустой ответ");
            }
        } catch (Exception e) {
            log.error("Ошибка при проверке LLM: {}", e.getMessage());
            return ServiceHealth.down("LLM недоступен: " + e.getMessage());
        }
    }
}

