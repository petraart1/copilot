package com.copilot.auth.controller;

import com.copilot.auth.dto.response.ImportHistoryResponse;
import com.copilot.auth.dto.response.ImportResponse;
import com.copilot.auth.dto.response.ImportStatusResponse;
import com.copilot.auth.model.ImportLog;
import com.copilot.auth.model.User;
import com.copilot.auth.repository.UserRepository;
import com.copilot.auth.service.UserImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/auth/import")
@RequiredArgsConstructor
@Tag(name = "User Import", description = "API для массового импорта пользователей")
public class UserImportController {

    private final UserImportService importService;
    private final UserRepository userRepository;

    @Operation(
            summary = "Импорт пользователей из CSV/XLSX",
            description = "Загружает файл с пользователями и запускает асинхронный импорт. " +
                    "Формат CSV: email,first_name,last_name,phone,telegram,department,role"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Импорт запущен"),
            @ApiResponse(responseCode = "400", description = "Неверный формат файла"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @PostMapping(value = "/users", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResponse> importUsers(@RequestParam("file") MultipartFile file) {
        log.info("Запрос на импорт пользователей. Файл: {}", file.getOriginalFilename());
        
        UUID userId = getCurrentUserId();
        
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл не может быть пустым");
        }

        ImportLog importLog = importService.startImport(file, userId);

        ImportResponse response = new ImportResponse(
                importLog.getId(),
                importLog.getStatus(),
                importLog.getTotalRecords(),
                importLog.getSuccessfulRecords(),
                importLog.getFailedRecords()
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(
            summary = "Получить статус импорта",
            description = "Возвращает текущий статус импорта по ID"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Статус импорта"),
            @ApiResponse(responseCode = "404", description = "Импорт не найден")
    })
    @GetMapping("/status/{importLogId}")
    public ResponseEntity<ImportStatusResponse> getImportStatus(@PathVariable UUID importLogId) {
        log.info("Запрос статуса импорта: {}", importLogId);
        
        ImportLog importLog = importService.getImportStatus(importLogId);
        
        List<String> errors = importLog.getErrorDetails() != null 
                ? List.of(importLog.getErrorDetails().split("\n"))
                : List.of();

        ImportStatusResponse response = new ImportStatusResponse(
                importLog.getId(),
                importLog.getStatus(),
                importLog.getTotalRecords(),
                importLog.getSuccessfulRecords() + importLog.getFailedRecords(),
                importLog.getFailedRecords(),
                errors
        );

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Получить историю импортов",
            description = "Возвращает историю всех импортов текущего пользователя"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "История импортов")
    })
    @GetMapping("/history")
    public ResponseEntity<ImportHistoryResponse> getImportHistory() {
        log.info("Запрос истории импортов");
        
        UUID userId = getCurrentUserId();
        List<ImportLog> imports = importService.getImportHistory(userId);

        List<ImportHistoryResponse.ImportInfo> importInfos = imports.stream()
                .map(il -> new ImportHistoryResponse.ImportInfo(
                        il.getId(),
                        il.getFileName(),
                        il.getTotalRecords(),
                        il.getSuccessfulRecords(),
                        il.getFailedRecords(),
                        il.getStatus(),
                        il.getCreatedAt()
                ))
                .collect(Collectors.toList());

        ImportHistoryResponse response = new ImportHistoryResponse(importInfos);
        return ResponseEntity.ok(response);
    }

    private UUID getCurrentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + email));
        return user.getId();
    }
}

