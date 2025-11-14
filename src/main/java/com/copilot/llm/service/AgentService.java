package com.copilot.llm.service;

import com.copilot.agent.dto.response.ActionResponse;
import com.copilot.agent.dto.response.ExecuteTaskResponse;
import com.copilot.agent.model.AgentAction;
import com.copilot.agent.repository.AgentActionRepository;
import com.copilot.auth.model.User;
import com.copilot.auth.repository.UserRepository;
import com.copilot.tools.email.EmailService;
import com.copilot.tools.meeting.MeetingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Сервис для выполнения задач AI-агентом с поддержкой tool calling
 * 
 * TODO: Полная реализация tool calling требует уточнения API LangChain4j 1.8.0
 * В текущей версии используется упрощенный подход с парсингом ответов LLM
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private final ChatModel chatModel;
    private final MeetingService meetingService;
    private final EmailService emailService;
    private final AgentActionRepository agentActionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_ITERATIONS = 5;

    /**
     * Выполняет задачу через AI-агента с поддержкой tool calling (без истории чата)
     * @param userRequest запрос пользователя
     * @param userEmail email пользователя (для определения организатора встреч)
     * @return результат выполнения задачи
     */
    @Transactional
    public ExecuteTaskResponse executeTask(String userRequest, String userEmail) {
        return executeTask(userRequest, userEmail, null);
    }

    /**
     * Выполняет задачу агентом с контекстом истории сообщений
     * @param userRequest запрос пользователя
     * @param userEmail email пользователя
     * @param chatHistory история сообщений из чата (может быть null). Формат: [{"role": "user|assistant", "content": "..."}]
     * @return результат выполнения задачи
     */
    @Transactional
    public ExecuteTaskResponse executeTask(String userRequest, String userEmail, List<Map<String, String>> chatHistory) {
        return executeTask(userRequest, userEmail, chatHistory, null);
    }

    /**
     * Выполняет задачу агентом с контекстом истории сообщений и chatId
     * @param userRequest запрос пользователя
     * @param userEmail email пользователя
     * @param chatHistory история сообщений из чата (может быть null). Формат: [{"role": "user|assistant", "content": "..."}]
     * @param chatId ID чата (для фильтрации контекста по чату)
     * @return результат выполнения задачи
     */
    @Transactional
    public ExecuteTaskResponse executeTask(String userRequest, String userEmail, List<Map<String, String>> chatHistory, UUID chatId) {
        log.info("Выполнение задачи агентом: {} (chatId: {})", userRequest, chatId);
        long startTime = System.currentTimeMillis();

        // Получаем информацию о пользователе
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + userEmail));

        // Получаем контекст из последних действий пользователя (для понимания контекста)
        // ВАЖНО: Если chatId указан, фильтруем действия по чату, иначе берем все действия пользователя
        List<AgentAction> recentActions;
        if (chatId != null) {
            recentActions = agentActionRepository
                    .findTop5ByUserIdAndChatIdOrderByCreatedAtDesc(user.getId(), chatId,
                            org.springframework.data.domain.PageRequest.of(0, 5));
        } else {
            recentActions = agentActionRepository
                    .findTop5ByUserIdOrderByCreatedAtDesc(user.getId(), 
                            org.springframework.data.domain.PageRequest.of(0, 5));
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(buildSystemPrompt(user, recentActions)));
        
        // Добавляем историю чата, если она есть
        if (chatHistory != null && !chatHistory.isEmpty()) {
            for (Map<String, String> historyItem : chatHistory) {
                String role = historyItem.get("role");
                String content = historyItem.get("content");
                if (role != null && content != null) {
                    if ("user".equals(role)) {
                        messages.add(UserMessage.from(content));
                    } else if ("assistant".equals(role)) {
                        messages.add(AiMessage.from(content));
                    }
                }
            }
        }
        
        // Добавляем текущий запрос пользователя
        messages.add(UserMessage.from(userRequest));

        List<ActionResponse> actions = new ArrayList<>();
        int iteration = 0;

        while (iteration < MAX_ITERATIONS) {
            iteration++;
            log.debug("Итерация агента: {}/{}", iteration, MAX_ITERATIONS);

            try {
                // Создаем запрос к LLM
                ChatRequest request = ChatRequest.builder()
                        .messages(messages)
                        .build();

                ChatResponse response = chatModel.chat(request);
                AiMessage aiMessage = response.aiMessage();

                // КРИТИЧНО: Обрабатываем случай, когда LLM возвращает пустой content
                // Это может произойти, когда модель возвращает только reasoning без content
                String text = aiMessage.text();
                
                // Логируем полный ответ для отладки
                log.info("LLM ответ (итерация {}): text={}, hasToolExecutionRequests={}", 
                        iteration, text != null ? (text.length() > 100 ? text.substring(0, 100) + "..." : text) : "null", 
                        aiMessage.hasToolExecutionRequests());
                
                // Если текст пустой, попросим LLM сгенерировать ответ снова с более явной инструкцией
                if (text == null || text.isEmpty()) {
                    log.warn("LLM вернул пустой content на итерации {}. Попытка повторного запроса с более явной инструкцией.", iteration);
                    
                    if (iteration < 3) {
                        // На первых итерациях просим LLM сгенерировать ответ
                        messages.add(UserMessage.from("ВАЖНО: Ты должен ответить на запрос пользователя конкретным действием. " +
                                "Если пользователь просит создать встречу или отправить письмо, ты ДОЛЖЕН вызвать соответствующий tool. " +
                                "Используй ТОЧНО этот формат (без изменений):\\n" +
                                "TOOL_CALL: schedule_meeting\\n" +
                                "ARGUMENTS: {\"title\": \"...\", \"start_time\": \"...\", \"attendees\": [...]}\\n\\n" +
                                "ИЛИ\\n" +
                                "TOOL_CALL: send_notification\\n" +
                                "ARGUMENTS: {\"recipients\": [...], \"subject\": \"...\", \"message\": \"...\"}\\n\\n" +
                                "НЕ возвращай пустой ответ. НЕ объясняй, что ты собираешься сделать - ВЫЗОВИ tool немедленно!"));
                        continue; // Повторяем итерацию
                    } else {
                        // На последующих итерациях, если ответ все еще пустой, завершаем с ошибкой
                        log.error("LLM не смог сгенерировать ответ после {} итераций", iteration);
                        ExecuteTaskResponse errorResponse = new ExecuteTaskResponse(
                                "error",
                                "Извините, не удалось выполнить запрос. LLM не сгенерировал ответ. Попробуйте переформулировать запрос или обратитесь к администратору.",
                                actions
                        );
                        long durationMs = System.currentTimeMillis() - startTime;
                        saveAgentAction(userEmail, userRequest, errorResponse, iteration, 
                                "LLM вернул пустой ответ после " + iteration + " итераций", durationMs, chatId);
                        return errorResponse;
                    }
                }

                // Проверяем, есть ли tool calls
                // TODO: Реализовать правильное извлечение tool calls из AiMessage
                // В LangChain4j 1.8.0 tool calls могут быть доступны через aiMessage.toolExecutionRequests()
                List<ToolExecutionRequest> toolCalls = extractToolCalls(aiMessage);
                
                // Всегда проверяем текст ответа на наличие tool calls
                boolean hasToolCallsInText = text != null && text.contains("TOOL_CALL:");
                
                // Если tool calls не извлечены через API, но есть в тексте, парсим напрямую
                if (hasToolCallsInText && (toolCalls.isEmpty() || toolCalls.stream().allMatch(tc -> tc == null))) {
                    log.info("Tool calls найдены в тексте, но не удалось создать объекты через API. Парсим и выполняем напрямую.");
                    List<Map<String, String>> parsedToolCalls = parseToolCallsFromTextDirectly(text);
                    
                    if (!parsedToolCalls.isEmpty()) {
                        // КРИТИЧНО: Проверяем, не выполняли ли мы уже этот tool в этой итерации
                        // Это предотвращает повторный вызов tool, если LLM вызывает его дважды
                        Set<String> executedToolsInThisIteration = new HashSet<>();
                        
                        // Выполняем tools напрямую, без создания ToolExecutionRequest
                        List<ToolExecutionResultMessage> toolResults = new ArrayList<>();
                        for (Map<String, String> parsedCall : parsedToolCalls) {
                            String toolName = parsedCall.get("name");
                            String toolArguments = parsedCall.get("arguments");
                            
                            if (toolName == null || toolArguments == null) {
                                log.warn("Пропускаем tool call с null именем или аргументами");
                                continue;
                            }
                            
                            // Проверяем, не выполняли ли мы уже этот tool с такими же аргументами в этой итерации
                            String toolCallKey = toolName + ":" + toolArguments;
                            if (executedToolsInThisIteration.contains(toolCallKey)) {
                                log.warn("Tool {} с аргументами {} уже выполнен в этой итерации. Пропускаем повторный вызов.", toolName, toolArguments);
                                continue;
                            }
                            
                            // Проверяем, не выполняли ли мы уже этот tool в предыдущих действиях
                            boolean alreadyExecuted = actions.stream()
                                    .anyMatch(action -> toolName.equals(action.name()) && 
                                            "completed".equals(action.status()));
                            
                            if (alreadyExecuted) {
                                log.warn("Tool {} уже выполнен в предыдущих действиях. Пропускаем повторный вызов.", toolName);
                                // Добавляем сообщение для LLM, что tool уже выполнен
                                String previousResult = actions.stream()
                                        .filter(a -> toolName.equals(a.name()) && "completed".equals(a.status()))
                                        .findFirst()
                                        .map(a -> {
                                            Object result = a.output().get("result");
                                            return result != null ? result.toString() : "уже выполнен";
                                        })
                                        .orElse("уже выполнен");
                                messages.add(AiMessage.from("Tool " + toolName + " уже выполнен. Результат: " + previousResult));
                                continue;
                            }
                            
                            log.info("Выполнение tool напрямую: {} с аргументами: {}", toolName, toolArguments);
                            executedToolsInThisIteration.add(toolCallKey);
                            
                            try {
                                String toolResult = executeToolSafely(toolName, toolArguments, userEmail);
                                
                                // Сохраняем информацию о действии
                                actions.add(new ActionResponse(
                                        toolName,
                                        "completed",
                                        Map.of("result", toolResult)
                                ));
                                
                                log.info("Tool {} выполнен успешно: {}", toolName, toolResult);
                                
                                // Создаем ToolExecutionResultMessage для продолжения диалога
                                // Пробуем создать ToolExecutionRequest для результата
                                ToolExecutionRequest toolCallForResult = createToolExecutionRequest(toolName, toolArguments);
                                if (toolCallForResult != null) {
                                    toolResults.add(ToolExecutionResultMessage.from(toolCallForResult, toolResult));
                                } else {
                                    // Если не удалось создать, просто добавляем результат в сообщения как текстовое сообщение
                                    // НЕ добавляем tool call в историю, только результат, чтобы LLM не повторял tool call
                                    messages.add(AiMessage.from(toolResult));
                                }
                            } catch (Exception e) {
                                log.error("Ошибка при выполнении tool {}: {}", toolName, e.getMessage(), e);
                                
                                actions.add(new ActionResponse(
                                        toolName,
                                        "failed",
                                        Map.of("error", e.getMessage())
                                ));
                                
                                messages.add(AiMessage.from("Ошибка при выполнении tool " + toolName + ": " + e.getMessage()));
                            }
                        }
                        
                        // Добавляем результаты в историю и запрашиваем финальный ответ от LLM
                        // ВАЖНО: После выполнения tool делаем еще один запрос к LLM для получения финального ответа
                        // Это предотвратит повторный вызов tool
                        if (!toolResults.isEmpty()) {
                            // Добавляем результаты выполнения tools
                            messages.addAll(toolResults);
                            // Продолжаем цикл для получения финального ответа от LLM на основе результатов выполнения tool
                            // На следующей итерации LLM увидит результаты и сформирует финальный ответ, не вызывая tool снова
                            continue;
                        } else if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof AiMessage) {
                            // Если результаты уже добавлены в messages в блоке try выше (как AiMessage),
                            // проверяем, есть ли tool calls в следующем ответе LLM
                            // Продолжаем цикл для следующей итерации
                            continue;
                        } else {
                            // Если результатов нет, продолжаем цикл для следующей итерации
                            continue;
                        }
                    }
                }
                
                // Если tool calls не найдены (ни через API, ни в тексте), завершаем работу
                if ((toolCalls.isEmpty() || toolCalls.stream().allMatch(tc -> tc == null)) && !hasToolCallsInText) {
                    // Нет tool calls - агент завершил работу
                    String finalResult = aiMessage.text();
                    if (finalResult == null || finalResult.isEmpty()) {
                        finalResult = "Задача выполнена";
                    }
                    log.info("Агент завершил выполнение задачи за {} итераций", iteration);
                    ExecuteTaskResponse finalResponse = new ExecuteTaskResponse("success", finalResult, actions);
                    long durationMs = System.currentTimeMillis() - startTime;
                    saveAgentAction(userEmail, userRequest, finalResponse, iteration, null, durationMs, chatId);
                    return finalResponse;
                }

                // Выполняем tool calls
                // КРИТИЧНО: Проверяем, не выполняли ли мы уже этот tool в этой итерации или в предыдущих действиях
                // Это предотвращает повторный вызов tool, если LLM вызывает его дважды
                Set<String> executedToolsInThisIteration = new HashSet<>();
                List<ToolExecutionResultMessage> toolResults = new ArrayList<>();
                for (ToolExecutionRequest toolCall : toolCalls) {
                    if (toolCall == null) {
                        log.error("ToolExecutionRequest равен null, пропускаем");
                        continue;
                    }
                    
                    String toolName = toolCall.name();
                    String toolArguments = toolCall.arguments();
                    
                    // Проверяем, не выполняли ли мы уже этот tool с такими же аргументами в этой итерации
                    String toolCallKey = toolName + ":" + toolArguments;
                    if (executedToolsInThisIteration.contains(toolCallKey)) {
                        log.warn("Tool {} с аргументами {} уже выполнен в этой итерации. Пропускаем повторный вызов.", toolName, toolArguments);
                        continue;
                    }
                    
                    // Проверяем, не выполняли ли мы уже этот tool в предыдущих действиях
                    boolean alreadyExecuted = actions.stream()
                            .anyMatch(action -> toolName.equals(action.name()) && 
                                    "completed".equals(action.status()));
                    
                    if (alreadyExecuted) {
                        log.warn("Tool {} уже выполнен в предыдущих действиях. Пропускаем повторный вызов.", toolName);
                        // Добавляем сообщение для LLM, что tool уже выполнен
                        String previousResult = actions.stream()
                                .filter(a -> toolName.equals(a.name()) && "completed".equals(a.status()))
                                .findFirst()
                                .map(a -> {
                                    Object result = a.output().get("result");
                                    return result != null ? result.toString() : "уже выполнен";
                                })
                                .orElse("уже выполнен");
                        messages.add(AiMessage.from("Tool " + toolName + " уже выполнен. Результат: " + previousResult));
                        continue;
                    }
                    
                    log.info("Выполнение tool: {} с аргументами: {}", toolName, toolArguments);
                    executedToolsInThisIteration.add(toolCallKey);
                    
                    try {
                        String toolResult = executeToolSafely(toolName, toolArguments, userEmail);
                        toolResults.add(ToolExecutionResultMessage.from(toolCall, toolResult));
                        
                        // Сохраняем информацию о действии
                        actions.add(new ActionResponse(
                                toolName,
                                "completed",
                                Map.of("result", toolResult)
                        ));
                        
                        log.info("Tool {} выполнен успешно", toolName);
                    } catch (Exception e) {
                        log.error("Ошибка при выполнении tool {}: {}", toolName, e.getMessage(), e);
                        toolResults.add(ToolExecutionResultMessage.from(toolCall, 
                                "Ошибка: " + e.getMessage()));
                        
                        actions.add(new ActionResponse(
                                toolName,
                                "failed",
                                Map.of("error", e.getMessage())
                        ));
                    }
                }

                // Добавляем результаты выполнения tools в историю сообщений
                // ВАЖНО: НЕ добавляем tool call (aiMessage) в историю, только результат,
                // чтобы LLM не повторял tool call на следующей итерации
                // Добавляем только результаты выполнения tools
                if (!toolResults.isEmpty()) {
                    messages.addAll(toolResults);
                } else {
                    // Если не удалось выполнить tool call, добавляем сообщение об ошибке
                    messages.add(aiMessage);
                }

            } catch (Exception e) {
                log.error("Ошибка на итерации {}: {}", iteration, e.getMessage(), e);
                
                // Обработка rate limit
                String errorMessage = e.getMessage();
                String userFriendlyMessage = errorMessage;
                if (errorMessage != null && (errorMessage.contains("429") || errorMessage.contains("rate") || errorMessage.contains("rate-limited"))) {
                    userFriendlyMessage = "Превышен лимит запросов к LLM. Проверьте лимиты вашего API ключа Google Gemini: https://aistudio.google.com/app/apikey";
                }
                
                ExecuteTaskResponse response = new ExecuteTaskResponse(
                        "error",
                        "Ошибка при выполнении задачи: " + userFriendlyMessage,
                        actions
                );
                long durationMs = System.currentTimeMillis() - startTime;
                saveAgentAction(userEmail, userRequest, response, iteration, userFriendlyMessage, durationMs, chatId);
                return response;
            }
        }

        // Достигнут максимум итераций
        log.warn("Достигнут максимум итераций ({})", MAX_ITERATIONS);
        ExecuteTaskResponse response = new ExecuteTaskResponse(
                "partial_success",
                "Задача выполнена частично. Достигнут максимум итераций.",
                actions
        );
        long durationMs = System.currentTimeMillis() - startTime;
        saveAgentAction(userEmail, userRequest, response, MAX_ITERATIONS, null, durationMs, chatId);
        return response;
    }

    /**
     * Сохраняет действие агента в БД для аудита
     */
    private void saveAgentAction(String userEmail, String task, ExecuteTaskResponse response, 
                                 int iterations, String errorMessage, long durationMs, UUID chatId) {
        try {
            UUID userId = userRepository.findByEmail(userEmail)
                    .map(user -> user.getId())
                    .orElse(null);

            if (userId == null) {
                log.warn("Не удалось найти пользователя для сохранения действия агента: {}", userEmail);
                return;
            }

            // Сохраняем основное действие задачи
            Map<String, Object> inputData = Map.of(
                    "task", task,
                    "iterations", iterations
            );

            Map<String, Object> outputData = new HashMap<>();
            outputData.put("result", response.result());
            outputData.put("status", response.status());
            if (response.actions() != null && !response.actions().isEmpty()) {
                outputData.put("actions", response.actions().stream()
                        .map(action -> Map.of(
                                "name", action.name(),
                                "status", action.status(),
                                "output", action.output()
                        ))
                        .collect(java.util.stream.Collectors.toList()));
            }

            AgentAction mainAction = AgentAction.builder()
                    .userId(userId)
                    .chatId(chatId) // Сохраняем chatId для фильтрации контекста по чату
                    .actionType("task_execution")
                    .inputData(inputData)
                    .outputData(outputData)
                    .status(response.status())
                    .errorMessage(errorMessage)
                    .durationMs((int) durationMs)
                    .build();

            agentActionRepository.save(mainAction);

            // Сохраняем отдельные действия инструментов, если они есть
            if (response.actions() != null && !response.actions().isEmpty()) {
                for (ActionResponse action : response.actions()) {
                    Map<String, Object> toolInputData = Map.of(
                            "task", task,
                            "tool", action.name()
                    );

                    Map<String, Object> toolOutputData = new HashMap<>();
                    toolOutputData.put("status", action.status());
                    toolOutputData.put("output", action.output());

                    AgentAction toolAction = AgentAction.builder()
                            .userId(userId)
                            .chatId(chatId) // Сохраняем chatId для фильтрации контекста по чату
                            .actionType(action.name())
                            .inputData(toolInputData)
                            .outputData(toolOutputData)
                            .status(action.status())
                            .errorMessage("failed".equals(action.status()) ? 
                                    String.valueOf(action.output().get("error")) : null)
                            .durationMs(null) // Длительность отдельных tools не отслеживается
                            .build();

                    agentActionRepository.save(toolAction);
                }
            }

            log.debug("Действие агента сохранено для пользователя: {} (chatId: {})", userId, chatId);
        } catch (Exception e) {
            log.error("Ошибка при сохранении действия агента: {}", e.getMessage(), e);
            // Не прерываем выполнение, если не удалось сохранить действие
        }
    }

    /**
     * Строит системный промпт для агента с информацией о пользователе
     */
    private String buildSystemPrompt(User user, List<AgentAction> recentActions) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Ты умный агент-помощник для владельца бизнеса.\n\n");
        
        // Информация о пользователе
        prompt.append("Информация о пользователе:\n");
        prompt.append(String.format("- Email: %s\n", user.getEmail()));
        if (user.getFirstName() != null && !user.getFirstName().isEmpty()) {
            prompt.append(String.format("- Имя: %s\n", user.getFirstName()));
        }
        if (user.getLastName() != null && !user.getLastName().isEmpty()) {
            prompt.append(String.format("- Фамилия: %s\n", user.getLastName()));
        }
        if (user.getDepartment() != null && !user.getDepartment().isEmpty()) {
            prompt.append(String.format("- Отдел: %s\n", user.getDepartment()));
        }
        if (user.getRole() != null && !user.getRole().isEmpty()) {
            prompt.append(String.format("- Роль: %s\n", user.getRole()));
        }
        prompt.append("\n");
        
        // Контекст из предыдущих действий
        if (!recentActions.isEmpty()) {
            prompt.append("Контекст из предыдущих действий пользователя:\n");
            for (AgentAction action : recentActions) {
                if (action.getInputData() != null && action.getInputData().containsKey("task")) {
                    String task = String.valueOf(action.getInputData().get("task"));
                    prompt.append(String.format("- Задача: %s (статус: %s)\n", 
                            task.length() > 100 ? task.substring(0, 100) + "..." : task,
                            action.getStatus()));
                }
            }
            prompt.append("\n");
        }
        
        prompt.append("Твоя задача: разобрать естественный язык пользователя и выполнить нужные действия через инструменты.\n\n");
        
        prompt.append("Доступные инструменты:\n");
        prompt.append("1. schedule_meeting - Запланировать встречу (создать Jitsi ссылку, добавить в календарь, отправить приглашения)\n");
        prompt.append("   Параметры: title (string), start_time (ISO 8601), duration_minutes (integer, default 60), \n");
        prompt.append("   attendees (array of emails), description (string, optional)\n");
        prompt.append("   ВАЖНО: Email организатора встречи - это email пользователя из информации выше.\n\n");
        
        prompt.append("2. send_notification - Отправить email уведомление\n");
        prompt.append("   Параметры: recipients (array of emails), subject (string), message (string)\n\n");
        
        prompt.append("3. compose_letter - Составить текст письма (без отправки)\n");
        prompt.append("   Параметры: recipient (email), subject (string), content (string)\n\n");
        
        // Добавляем список существующих пользователей для справки
        List<String> existingUserEmails = userRepository.findAll().stream()
                .map(User::getEmail)
                .filter(email -> email != null && !email.isEmpty())
                .limit(20) // Ограничиваем список для экономии токенов
                .toList();
        
        if (!existingUserEmails.isEmpty()) {
            prompt.append("Существующие пользователи в системе (для справки):\n");
            for (String email : existingUserEmails) {
                prompt.append("- ").append(email).append("\n");
            }
            prompt.append("\n");
        }
        
        prompt.append("Правила:\n");
        prompt.append("1. Если в запросе упоминается время \"завтра\", рассчитай дату исходя из текущей даты.\n");
        prompt.append("2. НЕ спрашивай email пользователя - используй email из информации о пользователе выше.\n");
        prompt.append("3. ВАЖНО: Используй ТОЛЬКО существующие email адреса из списка выше. НЕ выдумывай email адреса.\n");
        prompt.append("4. Если пользователь просит запланировать встречу с участником, которого нет в системе, сообщи ему об этом.\n");
        prompt.append("5. КРИТИЧНО: Если пользователь просит создать встречу, ОБЯЗАТЕЛЬНО используй tool schedule_meeting. НЕ отвечай текстом, что встреча создана - ВЫЗОВИ tool!\n");
        prompt.append("6. КРИТИЧНО: Если пользователь просит отправить письмо/уведомление, ОБЯЗАТЕЛЬНО используй tool send_notification. НЕ отвечай текстом, что письмо отправлено - ВЫЗОВИ tool!\n");
        prompt.append("7. Для планирования встреч ВСЕГДА используй tool schedule_meeting. Email организатора уже известен.\n");
        prompt.append("8. Для отправки уведомлений ВСЕГДА используй tool send_notification. Отправляй только существующим пользователям.\n");
        prompt.append("9. Если нужно только написать письмо без отправки, используй tool compose_letter.\n");
        prompt.append("10. НЕ утверждай, что действие выполнено, пока не вызовешь соответствующий tool и не получишь результат его выполнения.\n");
        prompt.append("11. После выполнения tool и получения результата, сообщи пользователю о результате.\n\n");
        
        prompt.append("Формат даты и времени: ISO 8601 (например: 2025-11-14T15:00:00)\n\n");
        
        prompt.append("КРИТИЧНО ВАЖНО: Если пользователь просит создать встречу или отправить письмо, ТЫ ДОЛЖЕН ВЫЗВАТЬ TOOL, а не просто ответить текстом!\n");
        prompt.append("Формат вызова tool:\n");
        prompt.append("TOOL_CALL: tool_name\n");
        prompt.append("ARGUMENTS: {\"param1\": \"value1\", \"param2\": \"value2\"}\n\n");
        
        prompt.append("Пример правильного ответа при запросе на создание встречи:\n");
        prompt.append("TOOL_CALL: schedule_meeting\n");
        prompt.append("ARGUMENTS: {\"title\": \"Стратегическое планирование\", \"start_time\": \"2025-11-14T16:00:00\", \"attendees\": [\"7b5b4c51-986f-4791-9930-f41db8af587d@mailslurp.biz\", \"88ea6271-5a14-42f9-823b-37c3d75b69d6@mailslurp.biz\"]}\n\n");
        
        prompt.append("НЕПРАВИЛЬНО: \"Встреча успешно запланирована\" (без вызова tool)\n");
        prompt.append("ПРАВИЛЬНО: Вызвать tool schedule_meeting, дождаться результата, затем сообщить пользователю\n\n");
        
        prompt.append("КРИТИЧНО: ТЫ ВСЕГДА ДОЛЖЕН ВЕРНУТЬ ТЕКСТОВЫЙ ОТВЕТ (content), ДАЖЕ ЕСЛИ ВЫЗЫВАЕШЬ TOOL!\n");
        prompt.append("НЕ возвращай пустой ответ (content: \"\"). Если вызываешь tool, верни вызов tool в формате выше.\n");
        prompt.append("Если tool уже выполнен, верни текстовый ответ с результатом выполнения.\n");
        prompt.append("НЕ используй только reasoning - всегда возвращай content с ответом или tool call.\n");
        prompt.append("Пример правильного ответа с tool call:\n");
        prompt.append("TOOL_CALL: schedule_meeting\n");
        prompt.append("ARGUMENTS: {\"title\": \"Встреча\", \"start_time\": \"2025-11-14T17:00:00\", \"attendees\": [\"email@example.com\"]}\n\n");
        
        return prompt.toString();
    }

    /**
     * Извлекает tool calls из ответа LLM
     * TODO: Реализовать правильное извлечение tool calls через API LangChain4j 1.8.0
     */
    private List<ToolExecutionRequest> extractToolCalls(AiMessage aiMessage) {
        List<ToolExecutionRequest> toolCalls = new ArrayList<>();
        
        // В LangChain4j 1.8.0 tool calls доступны через aiMessage.toolExecutionRequests()
        // Пока используем упрощенный подход - парсим текст ответа
        try {
            // Пытаемся получить tool execution requests через API
            if (aiMessage.hasToolExecutionRequests()) {
                toolCalls.addAll(aiMessage.toolExecutionRequests());
            } else {
                // Если tool calls не найдены через API, пытаемся парсить текст
                String text = aiMessage.text();
                if (text != null && text.contains("TOOL_CALL:")) {
                    log.debug("Парсинг tool calls из текста ответа LLM");
                    toolCalls.addAll(parseToolCallsFromText(text));
                }
            }
        } catch (Exception e) {
            log.error("Не удалось извлечь tool calls: {}", e.getMessage(), e);
        }
        
        return toolCalls;
    }

    /**
     * Парсит tool calls из текста напрямую в Map (без создания ToolExecutionRequest)
     * Используется когда не удается создать ToolExecutionRequest
     * @return список Map с ключами "name" и "arguments"
     */
    private List<Map<String, String>> parseToolCallsFromTextDirectly(String text) {
        List<Map<String, String>> toolCalls = new ArrayList<>();
        
        try {
            // Ищем все вхождения TOOL_CALL: в тексте
            int toolCallIndex = text.indexOf("TOOL_CALL:");
            while (toolCallIndex != -1) {
                // Находим начало tool call
                int toolCallStart = toolCallIndex;
                String toolCallSection = text.substring(toolCallStart);
                
                // Извлекаем имя tool
                int toolNameStart = toolCallStart + "TOOL_CALL:".length();
                int toolNameEnd = toolCallSection.indexOf("\n");
                if (toolNameEnd == -1) {
                    toolNameEnd = toolCallSection.length();
                }
                String toolName = text.substring(toolNameStart, toolCallStart + toolNameEnd).trim();
                
                // Ищем ARGUMENTS:
                int argumentsIndex = toolCallSection.indexOf("ARGUMENTS:");
                if (argumentsIndex != -1) {
                    int argumentsStart = toolCallStart + argumentsIndex + "ARGUMENTS:".length();
                    
                    // Извлекаем аргументы - ищем JSON объект
                    String argumentsSection = text.substring(argumentsStart).trim();
                    
                    // Пытаемся найти конец JSON объекта
                    // JSON может быть на одной строке или многострочным
                    StringBuilder argumentsBuilder = new StringBuilder();
                    int braceCount = 0;
                    boolean inString = false;
                    boolean escapeNext = false;
                    int i = 0;
                    
                    // Пропускаем пробелы в начале
                    while (i < argumentsSection.length() && 
                           (argumentsSection.charAt(i) == ' ' || argumentsSection.charAt(i) == '\n' || argumentsSection.charAt(i) == '\r')) {
                        i++;
                    }
                    
                    // Парсим JSON объект
                    for (; i < argumentsSection.length(); i++) {
                        char c = argumentsSection.charAt(i);
                        
                        if (escapeNext) {
                            argumentsBuilder.append(c);
                            escapeNext = false;
                            continue;
                        }
                        
                        if (c == '\\') {
                            escapeNext = true;
                            argumentsBuilder.append(c);
                            continue;
                        }
                        
                        if (c == '"') {
                            inString = !inString;
                            argumentsBuilder.append(c);
                            continue;
                        }
                        
                        if (!inString) {
                            if (c == '{') {
                                braceCount++;
                                argumentsBuilder.append(c);
                            } else if (c == '}') {
                                braceCount--;
                                argumentsBuilder.append(c);
                                if (braceCount == 0) {
                                    // Нашли конец JSON объекта
                                    break;
                                }
                            } else {
                                argumentsBuilder.append(c);
                            }
                        } else {
                            argumentsBuilder.append(c);
                        }
                    }
                    
                    String arguments = argumentsBuilder.toString().trim();
                    
                    // Убираем лишний текст после JSON (например, "**content:** Встреча успешно..." или другие символы)
                    // Ищем закрывающую скобку JSON и убираем все после нее
                    int lastValidBrace = arguments.lastIndexOf("}");
                    if (lastValidBrace != -1 && lastValidBrace < arguments.length() - 1) {
                        // Есть текст после закрывающей скобки - убираем его
                        String textAfterBrace = arguments.substring(lastValidBrace + 1).trim();
                        if (!textAfterBrace.isEmpty()) {
                            log.debug("Обнаружен лишний текст после JSON аргументов: {}. Удаляем его.", textAfterBrace);
                            arguments = arguments.substring(0, lastValidBrace + 1);
                        }
                    }
                    
                    // Дополнительная проверка: если в аргументах есть символы типа "**" или "*content*", убираем их
                    if (arguments.contains("**") || arguments.contains("*content*")) {
                        // Убираем все после последней закрывающей скобки
                        int lastBrace = arguments.lastIndexOf("}");
                        if (lastBrace != -1) {
                            arguments = arguments.substring(0, lastBrace + 1);
                        }
                    }
                    
                    if (!toolName.isEmpty() && !arguments.isEmpty()) {
                        toolCalls.add(Map.of(
                                "name", toolName,
                                "arguments", arguments
                        ));
                        log.debug("Извлечен tool call: {} с аргументами: {}", toolName, arguments);
                    }
                }
                
                // Ищем следующий TOOL_CALL:
                toolCallIndex = text.indexOf("TOOL_CALL:", toolCallStart + 1);
            }
        } catch (Exception e) {
            log.error("Ошибка при парсинге tool calls из текста: {}", e.getMessage(), e);
        }
        
        return toolCalls;
    }

    /**
     * Парсит tool calls из текстового ответа LLM
     * Формат: TOOL_CALL: tool_name\nARGUMENTS: {...}
     */
    private List<ToolExecutionRequest> parseToolCallsFromText(String text) {
        List<ToolExecutionRequest> toolCalls = new ArrayList<>();
        
        try {
            // Разбиваем текст на строки
            String[] lines = text.split("\n");
            String currentToolName = null;
            StringBuilder argumentsBuilder = new StringBuilder();
            boolean inArguments = false;
            
            for (String line : lines) {
                line = line.trim();
                
                if (line.startsWith("TOOL_CALL:")) {
                    // Сохраняем предыдущий tool call, если есть
                    if (currentToolName != null && argumentsBuilder.length() > 0) {
                        try {
                            String argumentsJson = argumentsBuilder.toString().trim();
                            // Создаем ToolExecutionRequest через рефлексию или используем существующий объект
                            // В LangChain4j ToolExecutionRequest может быть record или класс с определенным конструктором
                            // Пока используем простой подход - создаем через статический метод или конструктор
                            ToolExecutionRequest toolCall = createToolExecutionRequest(currentToolName, argumentsJson);
                            if (toolCall == null) {
                                // Если не удалось создать, пробуем через обертку
                                toolCall = createToolExecutionRequestWrapper(currentToolName, argumentsJson);
                            }
                            if (toolCall != null) {
                                toolCalls.add(toolCall);
                            } else {
                                log.error("Не удалось создать ToolExecutionRequest для {}, но продолжаем выполнение", currentToolName);
                            }
                        } catch (Exception e) {
                            log.warn("Не удалось создать tool call для {}: {}", currentToolName, e.getMessage());
                        }
                    }
                    
                    // Начинаем новый tool call
                    currentToolName = line.substring("TOOL_CALL:".length()).trim();
                    argumentsBuilder = new StringBuilder();
                    inArguments = false;
                } else if (line.startsWith("ARGUMENTS:")) {
                    inArguments = true;
                    String argsLine = line.substring("ARGUMENTS:".length()).trim();
                    argumentsBuilder.append(argsLine);
                } else if (inArguments && currentToolName != null) {
                    // Продолжаем собирать аргументы (могут быть многострочными)
                    argumentsBuilder.append(" ").append(line);
                }
            }
            
            // Сохраняем последний tool call
            if (currentToolName != null && argumentsBuilder.length() > 0) {
                try {
                    String argumentsJson = argumentsBuilder.toString().trim();
                    ToolExecutionRequest toolCall = createToolExecutionRequest(currentToolName, argumentsJson);
                    if (toolCall == null) {
                        // Если не удалось создать, пробуем через обертку
                        toolCall = createToolExecutionRequestWrapper(currentToolName, argumentsJson);
                    }
                    if (toolCall != null) {
                        toolCalls.add(toolCall);
                    } else {
                        log.error("Не удалось создать ToolExecutionRequest для {}, но продолжаем выполнение", currentToolName);
                    }
                } catch (Exception e) {
                    log.warn("Не удалось создать tool call для {}: {}", currentToolName, e.getMessage());
                }
            }
            
            if (!toolCalls.isEmpty()) {
                log.info("Извлечено {} tool calls из текста", toolCalls.size());
            }
        } catch (Exception e) {
            log.error("Ошибка при парсинге tool calls из текста: {}", e.getMessage(), e);
        }
        
        return toolCalls;
    }

    /**
     * Создает ToolExecutionRequest из имени и аргументов
     * Использует рефлексию для создания объекта, так как API может отличаться
     */
    private ToolExecutionRequest createToolExecutionRequest(String name, String arguments) {
        try {
            // Пробуем найти конструктор с двумя String параметрами (публичный)
            java.lang.reflect.Constructor<?>[] constructors = ToolExecutionRequest.class.getConstructors();
            for (java.lang.reflect.Constructor<?> constructor : constructors) {
                Class<?>[] paramTypes = constructor.getParameterTypes();
                if (paramTypes.length == 2 && 
                    paramTypes[0] == String.class && 
                    paramTypes[1] == String.class) {
                    return (ToolExecutionRequest) constructor.newInstance(name, arguments);
                }
            }
            
            // Пробуем найти все конструкторы (включая приватные) - для record
            java.lang.reflect.Constructor<?>[] allConstructors = ToolExecutionRequest.class.getDeclaredConstructors();
            for (java.lang.reflect.Constructor<?> constructor : allConstructors) {
                Class<?>[] paramTypes = constructor.getParameterTypes();
                if (paramTypes.length == 2 && 
                    paramTypes[0] == String.class && 
                    paramTypes[1] == String.class) {
                    constructor.setAccessible(true);
                    return (ToolExecutionRequest) constructor.newInstance(name, arguments);
                }
            }
            
            // Пробуем через статические методы (если есть)
            try {
                java.lang.reflect.Method fromMethod = ToolExecutionRequest.class.getMethod("from", String.class, String.class);
                return (ToolExecutionRequest) fromMethod.invoke(null, name, arguments);
            } catch (NoSuchMethodException e) {
                // Метод не найден, продолжаем
            }
            
            // Если ничего не помогло, логируем все доступные конструкторы для отладки
            log.warn("Не найден подходящий конструктор для ToolExecutionRequest. name={}, arguments={}", name, arguments);
            log.debug("Доступные конструкторы: {}", java.util.Arrays.toString(ToolExecutionRequest.class.getDeclaredConstructors()));
            return null;
        } catch (Exception e) {
            log.error("Ошибка при создании ToolExecutionRequest: {}", e.getMessage(), e);
            log.debug("Детали ошибки:", e);
            return null;
        }
    }

    /**
     * Простой класс-обертка для ToolExecutionRequest
     * Используется когда не удается создать ToolExecutionRequest через reflection
     * ToolExecutionRequest - это record, поэтому создаем обертку с теми же методами
     */
    private static class ToolExecutionRequestWrapper {
        private final String name;
        private final String arguments;

        public ToolExecutionRequestWrapper(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }

        public String name() {
            return name;
        }

        public String arguments() {
            return arguments;
        }

        @Override
        public String toString() {
            return "ToolExecutionRequest(name=" + name + ", arguments=" + arguments + ")";
        }
        
        // Преобразуем в ToolExecutionRequest через reflection
        public ToolExecutionRequest toToolExecutionRequest() {
            try {
                // Пробуем создать через reflection
                java.lang.reflect.Constructor<?>[] constructors = ToolExecutionRequest.class.getDeclaredConstructors();
                for (java.lang.reflect.Constructor<?> constructor : constructors) {
                    Class<?>[] paramTypes = constructor.getParameterTypes();
                    if (paramTypes.length == 2 && 
                        paramTypes[0] == String.class && 
                        paramTypes[1] == String.class) {
                        constructor.setAccessible(true);
                        return (ToolExecutionRequest) constructor.newInstance(name, arguments);
                    }
                }
            } catch (Exception e) {
                log.warn("Не удалось преобразовать обертку в ToolExecutionRequest: {}", e.getMessage());
            }
            return null;
        }
    }

    /**
     * Создает обертку для ToolExecutionRequest, если стандартный способ не работает
     */
    private ToolExecutionRequest createToolExecutionRequestWrapper(String name, String arguments) {
        ToolExecutionRequestWrapper wrapper = new ToolExecutionRequestWrapper(name, arguments);
        ToolExecutionRequest result = wrapper.toToolExecutionRequest();
        if (result != null) {
            return result;
        }
        // Если не удалось создать, используем обертку напрямую
        // Но это не сработает, так как ToolExecutionRequest - это не интерфейс
        // Поэтому просто возвращаем null и обрабатываем это в вызывающем коде
        log.error("КРИТИЧЕСКАЯ ОШИБКА: Не удалось создать ToolExecutionRequest для {} с аргументами {}", name, arguments);
        return null;
    }

    /**
     * Безопасно выполняет tool
     */
    private String executeToolSafely(String name, String argumentsJson, String userEmail) {
        try {
            // Парсим JSON аргументы
            Map<String, Object> args = parseJsonArguments(argumentsJson);
            
            return switch (name) {
                case "schedule_meeting" -> executeScheduleMeeting(args, userEmail);
                case "send_notification" -> executeSendNotification(args);
                case "compose_letter" -> executeComposeLetter(args);
                default -> throw new IllegalArgumentException("Неизвестный tool: " + name);
            };
        } catch (Exception e) {
            log.error("Ошибка при выполнении tool {}: {}", name, e.getMessage(), e);
            throw new RuntimeException("Ошибка выполнения tool " + name + ": " + e.getMessage(), e);
        }
    }

    /**
     * Выполняет tool schedule_meeting
     */
    private String executeScheduleMeeting(Map<String, Object> args, String organizerEmail) {
        String title = getStr(args, "title");
        String startTimeStr = getStr(args, "start_time");
        LocalDateTime startTime = LocalDateTime.parse(startTimeStr, DateTimeFormatter.ISO_DATE_TIME);
        Integer duration = getInt(args, "duration_minutes", 60);
        List<String> attendees = getList(args, "attendees");
        String description = getStrOr(args, "description", "");

        // Фильтруем участников: оставляем только существующих пользователей и валидные email
        List<String> validAttendees = filterValidAttendees(attendees, organizerEmail);
        
        // Проверяем, были ли отфильтрованы какие-то участники
        List<String> invalidAttendees = new ArrayList<>();
        for (String attendee : attendees) {
            if (attendee != null && !attendee.trim().isEmpty()) {
                String email = attendee.trim().toLowerCase();
                if (!userRepository.existsByEmail(email) && !email.equals(organizerEmail.toLowerCase())) {
                    invalidAttendees.add(email);
                }
            }
        }
        
        if (validAttendees.isEmpty()) {
            String errorMessage = "Нет валидных участников для встречи. ";
            if (!invalidAttendees.isEmpty()) {
                errorMessage += "Следующие email адреса не найдены в системе: " + String.join(", ", invalidAttendees) + ". ";
            }
            errorMessage += "Убедитесь, что email адреса указаны правильно и пользователи существуют в системе.";
            throw new RuntimeException(errorMessage);
        }
        
        if (!invalidAttendees.isEmpty()) {
            log.warn("Некоторые участники не найдены в системе и были исключены: {}", invalidAttendees);
        }

        log.info("Создание встречи '{}' для {} участников: {}", title, validAttendees.size(), validAttendees);

        MeetingService.MeetingResponse resp = meetingService.scheduleMeeting(
                title, startTime, duration, validAttendees, description, organizerEmail
        );
        
        return String.format("Встреча '%s' запланирована на %s. Ссылка: %s. Участники: %s",
                title, startTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
                resp.getMeetingUrl(), String.join(", ", validAttendees));
    }
    
    /**
     * Фильтрует список участников, оставляя только существующих пользователей
     */
    private List<String> filterValidAttendees(List<String> attendees, String organizerEmail) {
        List<String> validAttendees = new ArrayList<>();
        
        for (String attendee : attendees) {
            if (attendee == null || attendee.trim().isEmpty()) {
                continue;
            }
            
            String email = attendee.trim().toLowerCase();
            
            // Проверяем, существует ли пользователь в системе
            boolean userExists = userRepository.existsByEmail(email);
            
            if (userExists) {
                validAttendees.add(email);
                log.debug("Участник {} найден в системе", email);
            } else {
                log.warn("Участник {} не найден в системе, пропускаем", email);
            }
        }
        
        // Добавляем организатора, если его еще нет в списке
        if (!validAttendees.contains(organizerEmail.toLowerCase())) {
            validAttendees.add(organizerEmail.toLowerCase());
        }
        
        return validAttendees;
    }

    /**
     * Выполняет tool send_notification
     */
    private String executeSendNotification(Map<String, Object> args) {
        String[] recipients = getArray(args, "recipients");
        String subject = getStr(args, "subject");
        String message = getStr(args, "message");

        // Фильтруем получателей: оставляем только существующих пользователей
        List<String> validRecipients = new ArrayList<>();
        for (String recipient : recipients) {
            if (recipient == null || recipient.trim().isEmpty()) {
                continue;
            }
            
            String email = recipient.trim().toLowerCase();
            
            // Проверяем, существует ли пользователь в системе
            boolean userExists = userRepository.existsByEmail(email);
            
            if (userExists) {
                validRecipients.add(email);
                log.debug("Получатель {} найден в системе", email);
            } else {
                log.warn("Получатель {} не найден в системе, пропускаем", email);
            }
        }
        
        // Проверяем, были ли отфильтрованы какие-то получатели
        List<String> invalidRecipients = new ArrayList<>();
        for (String recipient : recipients) {
            if (recipient != null && !recipient.trim().isEmpty()) {
                String email = recipient.trim().toLowerCase();
                if (!userRepository.existsByEmail(email)) {
                    invalidRecipients.add(email);
                }
            }
        }
        
        if (validRecipients.isEmpty()) {
            String errorMessage = "Нет валидных получателей для уведомления. ";
            if (!invalidRecipients.isEmpty()) {
                errorMessage += "Следующие email адреса не найдены в системе: " + String.join(", ", invalidRecipients) + ". ";
            }
            errorMessage += "Убедитесь, что email адреса указаны правильно и пользователи существуют в системе.";
            throw new RuntimeException(errorMessage);
        }
        
        if (!invalidRecipients.isEmpty()) {
            log.warn("Некоторые получатели не найдены в системе и были исключены: {}", invalidRecipients);
        }

        emailService.sendBulkEmails(validRecipients.toArray(new String[0]), subject, message);
        return String.format("Уведомления отправлены %d получателям: %s", 
                validRecipients.size(), String.join(", ", validRecipients));
    }

    /**
     * Выполняет tool compose_letter
     */
    private String executeComposeLetter(Map<String, Object> args) {
        String recipient = getStr(args, "recipient");
        String subject = getStr(args, "subject");
        String content = getStr(args, "content");
        
        return String.format("Письмо составлено для %s:\nТема: %s\n\n%s", recipient, subject, content);
    }

    /**
     * Парсит JSON аргументы в Map
     */
    private Map<String, Object> parseJsonArguments(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Ошибка при парсинге JSON аргументов: {}", json, e);
            throw new RuntimeException("Не удалось распарсить аргументы: " + e.getMessage(), e);
        }
    }

    private String getStr(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) throw new IllegalArgumentException("Отсутствует обязательный параметр: " + k);
        return String.valueOf(v);
    }

    private Integer getInt(Map<String, Object> m, String k, Integer def) {
        Object v = m.get(k);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).intValue();
        return Integer.parseInt(String.valueOf(v));
    }

    private String getStrOr(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v == null ? def : String.valueOf(v);
    }

    private String[] getArray(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toArray(String[]::new);
        }
        throw new IllegalArgumentException("Параметр " + k + " должен быть массивом");
    }

    private List<String> getList(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        throw new IllegalArgumentException("Параметр " + k + " должен быть списком");
    }
}
