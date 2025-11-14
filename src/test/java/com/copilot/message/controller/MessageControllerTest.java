package com.copilot.message.controller;

import com.copilot.auth.model.User;
import com.copilot.auth.repository.UserRepository;
import com.copilot.message.dto.request.SendMessageRequest;
import com.copilot.message.dto.response.MessageResponse;
import com.copilot.message.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = MessageController.class, excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
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
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MessageService messageService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private com.copilot.security.JwtService jwtService;

    private UUID userId;
    private UUID chatId;
    private String userEmail;
    private User testUser;
    private MessageResponse testMessageResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        chatId = UUID.randomUUID();
        userEmail = "test@example.com";
        
        testUser = User.builder()
                .id(userId)
                .email(userEmail)
                .password("encoded")
                .isActive(true)
                .build();

        testMessageResponse = new MessageResponse(
                UUID.randomUUID(),
                "Hello",
                "Hi there!",
                "agent_response",
                null,
                null,
                null,
                LocalDateTime.now()
        );
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldSendMessageSuccessfully() throws Exception {
        // Arrange
        SendMessageRequest request = new SendMessageRequest("Hello");

        when(userRepository.findByEmailAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(testUser));
        when(messageService.sendMessage(eq(chatId), eq(userId), eq(userEmail), any(SendMessageRequest.class)))
                .thenReturn(testMessageResponse);

        // Act & Assert
        mockMvc.perform(post("/chats/{chatId}/messages", chatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userMessage").value("Hello"))
                .andExpect(jsonPath("$.aiResponse").value("Hi there!"))
                .andExpect(jsonPath("$.responseType").value("agent_response"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldGetMessagesSuccessfully() throws Exception {
        // Arrange
        Page<MessageResponse> messagePage = new PageImpl<>(
                List.of(testMessageResponse), 
                PageRequest.of(0, 50), 
                1
        );

        when(userRepository.findByEmailAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(testUser));
        when(messageService.getChatMessages(eq(chatId), eq(userId), any())).thenReturn(messagePage);

        // Act & Assert
        mockMvc.perform(get("/chats/{chatId}/messages", chatId)
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].userMessage").value("Hello"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldReturnBadRequestWhenMessageIsEmpty() throws Exception {
        // Arrange
        SendMessageRequest request = new SendMessageRequest("");

        when(userRepository.findByEmailAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(testUser));

        // Act & Assert
        mockMvc.perform(post("/chats/{chatId}/messages", chatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(messageService, never()).sendMessage(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldReturnBadRequestWhenMessageIsNull() throws Exception {
        // Arrange
        SendMessageRequest request = new SendMessageRequest(null);

        when(userRepository.findByEmailAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(testUser));

        // Act & Assert
        mockMvc.perform(post("/chats/{chatId}/messages", chatId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(messageService, never()).sendMessage(any(), any(), any(), any());
    }

    @Test
    void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/chats/{chatId}/messages", chatId))
                .andExpect(status().isUnauthorized());
    }
}

