package com.copilot.llm.controller;

import com.copilot.llm.dto.GenerateRequest;
import com.copilot.llm.service.LLMService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/stream")
@RequiredArgsConstructor
@Tag(name = "LLM Streaming", description = "API для потоковой генерации текста через LLM")
public class LLMController {

    private final LLMService llmService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Operation(
            summary = "Потоковая генерация текста (Server-Sent Events)",
            description = "Генерирует текст через LLM и отправляет токены в реальном времени через SSE. " +
                    "Каждый токен отправляется как отдельное событие 'token', финальный ответ - как событие 'complete'."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Streaming начат"),
            @ApiResponse(responseCode = "400", description = "Неверные данные запроса"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGenerate(@Valid @RequestBody GenerateRequest request) {
        log.info("Запрос на streaming генерацию: {}", request.prompt());

        SseEmitter emitter = new SseEmitter(60000L); // 60 секунд timeout

        executorService.execute(() -> {
            try {
                llmService.generateStream(
                        request.prompt(),
                        // onToken - отправляем каждый токен
                        token -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("token")
                                        .data(token)
                                        .build());
                            } catch (IOException e) {
                                log.error("Ошибка при отправке токена: {}", e.getMessage(), e);
                                emitter.completeWithError(e);
                            }
                        },
                        // onComplete - отправляем финальный ответ и закрываем stream
                        fullText -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("complete")
                                        .data(fullText)
                                        .build());
                                emitter.complete();
                                log.debug("Streaming завершен успешно");
                            } catch (IOException e) {
                                log.error("Ошибка при завершении streaming: {}", e.getMessage(), e);
                                emitter.completeWithError(e);
                            }
                        }
                );
            } catch (Exception e) {
                log.error("Ошибка при streaming генерации: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        // Обработка ошибок и таймаутов
        emitter.onCompletion(() -> log.debug("SSE emitter завершен"));
        emitter.onTimeout(() -> {
            log.warn("SSE emitter timeout");
            emitter.complete();
        });
        emitter.onError((ex) -> {
            log.error("Ошибка SSE emitter: {}", ex.getMessage(), ex);
            emitter.completeWithError(ex);
        });

        return emitter;
    }

    @Operation(
            summary = "Генерация текста (без streaming)",
            description = "Генерирует текст через LLM и возвращает полный ответ сразу"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Текст успешно сгенерирован"),
            @ApiResponse(responseCode = "400", description = "Неверные данные запроса"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @PostMapping("/generate/sync")
    public ResponseEntity<String> generateSync(@Valid @RequestBody GenerateRequest request) {
        log.info("Запрос на синхронную генерацию: {}", request.prompt());

        String response = llmService.generate(request.prompt());
        return ResponseEntity.ok(response);
    }
}

