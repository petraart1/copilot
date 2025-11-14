package com.copilot.auth.controller;

import com.copilot.auth.dto.request.ChangePasswordRequest;
import com.copilot.auth.dto.request.LoginRequest;
import com.copilot.auth.dto.request.RegisterRequest;
import com.copilot.auth.dto.request.ResetPasswordRequest;
import com.copilot.auth.dto.response.AuthResponse;
import com.copilot.auth.dto.response.LoginResponse;
import com.copilot.auth.dto.response.UserResponse;
import com.copilot.auth.service.AuthService;
import com.copilot.dto.response.RegisterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "API для аутентификации и управления пользователями")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Регистрация пользователя", description = "Создает нового пользователя в системе")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Пользователь успешно зарегистрирован"),
            @ApiResponse(responseCode = "400", description = "Неверные данные запроса или пользователь уже существует")
    })
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        log.info("Запрос на регистрацию: {}", req.email());
        RegisterResponse response = authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Авторизация пользователя", description = "Аутентифицирует пользователя и возвращает токены")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Успешная авторизация"),
            @ApiResponse(responseCode = "401", description = "Неверные учетные данные")
    })
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        log.info("Запрос на вход: {}", req.email());
        LoginResponse response = authService.login(req);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Обновление токена", description = "Обновляет access token используя refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Токены успешно обновлены"),
            @ApiResponse(responseCode = "401", description = "Неверный refresh token")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestHeader("Authorization") String authHeader) {
        log.info("Запрос на обновление токена");
        String refreshToken = authHeader.startsWith("Bearer ") 
                ? authHeader.substring(7) 
                : authHeader;
        AuthResponse response = authService.refresh(refreshToken);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Смена пароля", description = "Изменяет пароль текущего пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Пароль успешно изменен"),
            @ApiResponse(responseCode = "400", description = "Неверный текущий пароль"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        log.info("Запрос на смену пароля");
        authService.changePassword(req);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Получить текущего пользователя", description = "Возвращает информацию о текущем авторизованном пользователе")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Информация о пользователе"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован")
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser() {
        log.info("Запрос информации о текущем пользователе");
        UserResponse response = authService.getCurrentUser();
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Сброс пароля", description = "Сбрасывает пароль используя токен из email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Пароль успешно сброшен"),
            @ApiResponse(responseCode = "400", description = "Неверный или истекший токен")
    })
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        log.info("Запрос на сброс пароля");
        authService.resetPassword(req);
        return ResponseEntity.noContent().build();
    }
}
