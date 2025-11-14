package com.copilot.llm.service;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class LLMService {

    private final ChatModel chatModel;

    public String generate(String prompt) {
        log.debug("Генерация ответа для промпта: {}", prompt);

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from("Ты полезный AI-ассистент. Отвечай кратко и по делу."),
                            UserMessage.from(prompt)
                    ))
                    .build();

            ChatResponse response = chatModel.chat(request);
            String text = response.aiMessage().text();

            if (text == null || text.isEmpty()) {
                return "Не удалось сгенерировать ответ";
            }

            return text;

        } catch (Exception e) {
            log.error("Ошибка при генерации ответа: {}", e.getMessage(), e);
            
            // Обработка rate limit
            String errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains("429") || errorMessage.contains("rate") || errorMessage.contains("rate-limited"))) {
                throw new RuntimeException("Превышен лимит запросов к LLM. Проверьте лимиты вашего API ключа Google Gemini: https://aistudio.google.com/app/apikey", e);
            }
            
            throw new RuntimeException("Не удалось сгенерировать ответ: " + e.getMessage(), e);
        }
    }

    public void generateStream(String prompt, Consumer<String> onToken, Consumer<String> onComplete) {
        log.debug("Генерация streaming ответа для промпта: {}", prompt);

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(
                            SystemMessage.from("Ты полезный AI-ассистент. Отвечай кратко и по делу."),
                            UserMessage.from(prompt)
                    ))
                    .build();

            ChatResponse response = chatModel.chat(request);
            String fullText = response.aiMessage().text();

            if (fullText == null || fullText.isEmpty()) {
                onComplete.accept("Не удалось сгенерировать ответ");
                return;
            }

            // Эмулируем streaming: разбиваем текст на слова и отправляем по одному
            // В реальности нужно использовать StreamingChatLanguageModel, если доступен
            String[] words = fullText.split("\\s+");
            StringBuilder accumulated = new StringBuilder();

            for (int i = 0; i < words.length; i++) {
                String word = words[i];
                if (i > 0) {
                    accumulated.append(" ");
                }
                accumulated.append(word);
                
                String token = word + (i < words.length - 1 ? " " : "");
                onToken.accept(token);

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            onComplete.accept(accumulated.toString());

        } catch (Exception e) {
            log.error("Ошибка при streaming генерации: {}", e.getMessage(), e);
            
            // Обработка rate limit
            String errorMessage = e.getMessage();
            if (errorMessage != null && (errorMessage.contains("429") || errorMessage.contains("rate") || errorMessage.contains("rate-limited"))) {
                onComplete.accept("Ошибка: Превышен лимит запросов к LLM. Проверьте лимиты вашего API ключа Google Gemini: https://aistudio.google.com/app/apikey");
                return;
            }
            
            onComplete.accept("Ошибка при генерации ответа: " + e.getMessage());
        }
    }
}

