package com.copilot.agent.service;

import com.copilot.agent.dto.response.AgentActionResponse;
import com.copilot.agent.model.AgentAction;
import com.copilot.agent.repository.AgentActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentHistoryServiceTest {

    @Mock
    private AgentActionRepository agentActionRepository;

    @InjectMocks
    private AgentHistoryService agentHistoryService;

    private UUID userId;
    private AgentAction testAction;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testAction = AgentAction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .chatId(null)
                .actionType("task_execution")
                .inputData(Map.of("task", "Test task", "iterations", 1))
                .outputData(Map.of("result", "Success", "status", "success"))
                .status("success")
                .errorMessage(null)
                .durationMs(1000)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldGetUserHistoryWithPagination() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<AgentAction> actionPage = new PageImpl<>(List.of(testAction), pageable, 1);

        when(agentActionRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable))
                .thenReturn(actionPage);

        // Act
        Page<AgentActionResponse> result = agentHistoryService.getUserHistory(userId, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        AgentActionResponse response = result.getContent().get(0);
        assertEquals("task_execution", response.actionType());
        assertEquals("success", response.status());
        assertEquals(1000, response.durationMs());
        assertNotNull(response.inputData());
        assertNotNull(response.outputData());

        verify(agentActionRepository, times(1))
                .findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Test
    void shouldGetUserHistoryWithFilters() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        String status = "success";
        String actionType = "task_execution";
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();
        Page<AgentAction> actionPage = new PageImpl<>(List.of(testAction), pageable, 1);

        when(agentActionRepository.findByUserIdWithFilters(
                eq(userId), eq(status), eq(actionType), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(actionPage);

        // Act
        Page<AgentActionResponse> result = agentHistoryService.getUserHistoryWithFilters(
                userId, status, actionType, from, to, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("success", result.getContent().get(0).status());
        assertEquals("task_execution", result.getContent().get(0).actionType());

        verify(agentActionRepository, times(1))
                .findByUserIdWithFilters(userId, status, actionType, from, to, pageable);
    }

    @Test
    void shouldGetUserHistoryWithPartialFilters() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        String status = "success";
        Page<AgentAction> actionPage = new PageImpl<>(List.of(testAction), pageable, 1);

        when(agentActionRepository.findByUserIdWithFilters(
                eq(userId), eq(status), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(actionPage);

        // Act
        Page<AgentActionResponse> result = agentHistoryService.getUserHistoryWithFilters(
                userId, status, null, null, null, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(agentActionRepository, times(1))
                .findByUserIdWithFilters(userId, status, null, null, null, pageable);
    }

    @Test
    void shouldReturnEmptyPageWhenNoActionsFound() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<AgentAction> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(agentActionRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable))
                .thenReturn(emptyPage);

        // Act
        Page<AgentActionResponse> result = agentHistoryService.getUserHistory(userId, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());

        verify(agentActionRepository, times(1))
                .findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Test
    void shouldMapAgentActionToResponseCorrectly() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        AgentAction actionWithError = AgentAction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .chatId(UUID.randomUUID())
                .actionType("schedule_meeting")
                .inputData(Map.of("title", "Meeting", "attendees", List.of("user@example.com")))
                .outputData(Map.of("error", "Failed to create meeting"))
                .status("error")
                .errorMessage("Calendar service unavailable")
                .durationMs(500)
                .createdAt(LocalDateTime.now())
                .build();

        Page<AgentAction> actionPage = new PageImpl<>(List.of(actionWithError), pageable, 1);

        when(agentActionRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable))
                .thenReturn(actionPage);

        // Act
        Page<AgentActionResponse> result = agentHistoryService.getUserHistory(userId, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        AgentActionResponse response = result.getContent().get(0);
        assertEquals("schedule_meeting", response.actionType());
        assertEquals("error", response.status());
        assertEquals("Calendar service unavailable", response.errorMessage());
        assertEquals(500, response.durationMs());
        assertNotNull(response.chatId());
        assertNotNull(response.inputData());
        assertNotNull(response.outputData());
    }
}

