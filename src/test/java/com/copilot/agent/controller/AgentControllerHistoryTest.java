package com.copilot.agent.controller;

import com.copilot.agent.dto.response.AgentActionResponse;
import com.copilot.agent.service.AgentHistoryService;
import com.copilot.auth.model.User;
import com.copilot.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AgentController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-testing-purposes-only-min-32-chars",
        "jwt.issuer=com.copilot",
        "jwt.access-token-ttl=15m",
        "jwt.refresh-token-ttl=7d",
        "spring.redis.host=localhost",
        "spring.redis.port=6379",
        "mailslurp.api-key=test-key",
        "calendar.caldav.base-url=http://localhost:5232",
        "meetings.jitsi.base-url=https://meet.jit.si"
})
class AgentControllerHistoryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentHistoryService agentHistoryService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private com.copilot.llm.service.AgentService agentService;

    @MockBean
    private com.copilot.security.JwtService jwtService;

    private UUID userId;
    private String userEmail;
    private User testUser;
    private AgentActionResponse testActionResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userEmail = "test@example.com";
        
        testUser = User.builder()
                .id(userId)
                .email(userEmail)
                .password("encoded")
                .isActive(true)
                .build();

        testActionResponse = new AgentActionResponse(
                UUID.randomUUID(),
                null,
                "task_execution",
                Map.of("task", "Test task"),
                Map.of("result", "Success"),
                "success",
                null,
                1000,
                LocalDateTime.now()
        );
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldGetHistorySuccessfully() throws Exception {
        // Arrange
        Page<AgentActionResponse> actionPage = new PageImpl<>(
                List.of(testActionResponse), 
                PageRequest.of(0, 20), 
                1
        );

        when(userRepository.findByEmailAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(testUser));
        when(agentHistoryService.getUserHistory(eq(userId), any())).thenReturn(actionPage);

        // Act & Assert
        mockMvc.perform(get("/agent/history")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].actionType").value("task_execution"))
                .andExpect(jsonPath("$.content[0].status").value("success"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldGetHistoryWithStatusFilter() throws Exception {
        // Arrange
        Page<AgentActionResponse> actionPage = new PageImpl<>(
                List.of(testActionResponse), 
                PageRequest.of(0, 20), 
                1
        );

        when(userRepository.findByEmailAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(testUser));
        when(agentHistoryService.getUserHistoryWithFilters(
                eq(userId), eq("success"), eq(null), eq(null), eq(null), any()))
                .thenReturn(actionPage);

        // Act & Assert
        mockMvc.perform(get("/agent/history")
                        .param("status", "success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("success"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldGetHistoryWithActionTypeFilter() throws Exception {
        // Arrange
        Page<AgentActionResponse> actionPage = new PageImpl<>(
                List.of(testActionResponse), 
                PageRequest.of(0, 20), 
                1
        );

        when(userRepository.findByEmailAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(testUser));
        when(agentHistoryService.getUserHistoryWithFilters(
                eq(userId), eq(null), eq("schedule_meeting"), eq(null), eq(null), any()))
                .thenReturn(actionPage);

        // Act & Assert
        mockMvc.perform(get("/agent/history")
                        .param("actionType", "schedule_meeting"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldGetHistoryWithDateRangeFilter() throws Exception {
        // Arrange
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();
        Page<AgentActionResponse> actionPage = new PageImpl<>(
                List.of(testActionResponse), 
                PageRequest.of(0, 20), 
                1
        );

        when(userRepository.findByEmailAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(testUser));
        when(agentHistoryService.getUserHistoryWithFilters(
                eq(userId), eq(null), eq(null), any(), any(), any()))
                .thenReturn(actionPage);

        // Act & Assert
        mockMvc.perform(get("/agent/history")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldGetHistoryWithAllFilters() throws Exception {
        // Arrange
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();
        Page<AgentActionResponse> actionPage = new PageImpl<>(
                List.of(testActionResponse), 
                PageRequest.of(0, 20), 
                1
        );

        when(userRepository.findByEmailAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(testUser));
        when(agentHistoryService.getUserHistoryWithFilters(
                eq(userId), eq("success"), eq("task_execution"), any(), any(), any()))
                .thenReturn(actionPage);

        // Act & Assert
        mockMvc.perform(get("/agent/history")
                        .param("status", "success")
                        .param("actionType", "task_execution")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/agent/history"))
                .andExpect(status().isUnauthorized());
    }
}

