package com.copilot.message.service;

import com.copilot.chat.model.Chat;
import com.copilot.chat.repository.ChatRepository;
import com.copilot.llm.service.AgentService;
import com.copilot.message.dto.request.SendMessageRequest;
import com.copilot.message.dto.response.MessageResponse;
import com.copilot.message.model.Message;
import com.copilot.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final AgentService agentService;

    @Transactional
    public MessageResponse sendMessage(UUID chatId, UUID userId, String userEmail, SendMessageRequest request) {
        log.info("Отправка сообщения в чат {} пользователем {}", chatId, userId);

        Chat chat = chatRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new RuntimeException("Чат не найден или у вас нет доступа"));

        Message message = Message.builder()
                .chatId(chatId)
                .userMessage(request.message())
                .build();

        try {
            // Получаем историю сообщений из чата для контекста
            // Берем только финальные ответы агента (без tool calls)
            List<Message> previousMessages = messageRepository.findAllByChatIdOrderByCreatedAtAsc(chatId);
            List<Map<String, String>> chatHistory = new ArrayList<>();
            
            // Добавляем только последние сообщения с финальными ответами (не tool calls)
            // Обрабатываем в хронологическом порядке (от старых к новым)
            for (Message m : previousMessages) {
                if (m.getUserMessage() != null && m.getAiResponse() != null) {
                    String aiResponse = m.getAiResponse();
                    // Пропускаем сообщения, которые содержат только tool calls (короткие с TOOL_CALL:)
                    // Оставляем только финальные ответы агента
                    if (!aiResponse.contains("TOOL_CALL:") || 
                        (aiResponse.contains("TOOL_CALL:") && aiResponse.length() > 300)) {
                        // Это финальный ответ после выполнения tool или обычный ответ
                        chatHistory.add(Map.of("role", "user", "content", m.getUserMessage()));
                        // Убираем tool calls из ответа, если они есть
                        String cleanedResponse = aiResponse;
                        if (aiResponse.contains("TOOL_CALL:")) {
                            // Находим последний абзац после tool calls
                            String[] parts = aiResponse.split("TOOL_CALL:");
                            if (parts.length > 1) {
                                // Берем текст после последнего tool call
                                String lastPart = parts[parts.length - 1];
                                if (lastPart.contains("\n\n")) {
                                    cleanedResponse = lastPart.substring(lastPart.indexOf("\n\n") + 2).trim();
                                } else {
                                    cleanedResponse = lastPart.substring(lastPart.indexOf("\n") + 1).trim();
                                }
                            }
                        }
                        chatHistory.add(Map.of("role", "assistant", "content", cleanedResponse));
                    }
                }
            }
            
            // Ограничиваем историю последними 10 сообщениями (5 пар)
            if (chatHistory.size() > 10) {
                chatHistory = chatHistory.subList(chatHistory.size() - 10, chatHistory.size());
            }
            
            com.copilot.agent.dto.response.ExecuteTaskResponse agentResponse = 
                    agentService.executeTask(request.message(), userEmail, chatHistory, chatId);
            message.setAiResponse(agentResponse.result());
            message.setResponseType("agent_response");
            
            if (agentResponse.actions() != null && !agentResponse.actions().isEmpty()) {
                message.setContextData(Map.of(
                        "actions", agentResponse.actions().stream()
                                .map(action -> Map.of(
                                        "name", action.name(),
                                        "status", action.status(),
                                        "output", action.output()
                                ))
                                .collect(Collectors.toList())
                ));
            }
        } catch (Exception e) {
            log.error("Ошибка при обработке сообщения агентом: {}", e.getMessage(), e);
            message.setAiResponse("Произошла ошибка при обработке запроса: " + e.getMessage());
            message.setResponseType("error");
        }

        Message saved = messageRepository.save(message);

        chat.setUpdatedAt(java.time.LocalDateTime.now());
        chatRepository.save(chat);

        log.info("Сообщение сохранено: {}", saved.getId());
        return toMessageResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getChatMessages(UUID chatId, UUID userId, Pageable pageable) {
        log.debug("Получение сообщений чата {} пользователем {}", chatId, userId);

        chatRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new RuntimeException("Чат не найден или у вас нет доступа"));

        Page<Message> messages = messageRepository.findAllByChatIdOrderByCreatedAtDesc(chatId, pageable);
        return messages.map(this::toMessageResponse);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getAllChatMessages(UUID chatId, UUID userId) {
        log.debug("Получение всех сообщений чата {} пользователем {}", chatId, userId);

        chatRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new RuntimeException("Чат не найден или у вас нет доступа"));

        List<Message> messages = messageRepository.findAllByChatIdOrderByCreatedAtDesc(chatId);
        return messages.stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());
    }

    private MessageResponse toMessageResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getUserMessage(),
                message.getAiResponse(),
                message.getResponseType(),
                message.getPromptTokens(),
                message.getCompletionTokens(),
                message.getContextData(),
                message.getCreatedAt()
        );
    }
}

