package com.copilot.tools.calendar.controller;

import com.copilot.tools.calendar.CalendarService;
import com.copilot.tools.calendar.dto.CreateEventRequest;
import com.copilot.tools.calendar.dto.EventResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
@Tag(name = "Calendar", description = "API для работы с календарем")
public class CalendarController {

    private final CalendarService calendarService;

    @Operation(
            summary = "Создать событие в календаре",
            description = "Создает событие в календаре для всех указанных участников. " +
                    "Событие будет добавлено в календарь каждого участника через CalDAV."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Событие успешно создано"),
            @ApiResponse(responseCode = "400", description = "Неверные данные запроса"),
            @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
            @ApiResponse(responseCode = "404", description = "Участник не найден")
    })
    @PostMapping("/events")
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            Authentication authentication) {
        String organizerEmail = getCurrentUserEmail(authentication);
        log.info("Запрос на создание события от пользователя: {}", organizerEmail);

        EventResponse event = calendarService.createEvent(request, organizerEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    private String getCurrentUserEmail(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("Пользователь не авторизован");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        return principal.toString();
    }
}

