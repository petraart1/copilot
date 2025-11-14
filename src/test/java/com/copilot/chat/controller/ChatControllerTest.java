package com.copilot.chat.controller;

import com.copilot.auth.model.User;
import com.copilot.auth.repository.UserRepository;
import com.copilot.chat.dto.request.CreateChatRequest;
import com.copilot.chat.dto.response.ChatResponse;
import com.copilot.chat.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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

@WebMvcTest(controllers = ChatController.class)
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
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private com.copilot.security.JwtService jwtService;

    private UUID userId;
    private UUID chatId;
    private String userEmail;
    private User testUser;
    private ChatResponse testChatResponse;

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

        testChatResponse = new ChatResponse(
                chatId,
                "Test Chat",
                false,
                LocalDateTime.now(),
                LocalDateTime.now(),
                0L
        );
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldCreateChatSuccessfully() throws Exception {
        // Arrange
        CreateChatRequest request = new CreateChatRequest("New Chat");
        ChatResponse response = new ChatResponse(
                chatId,
                "New Chat",
                false,
                LocalDateTime.now(),
                LocalDateTime.now(),
                0L
        );

        when(userRepository.findByEmailAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(testUser));
        when(chatService.createChat(eq(userId), any(CreateChatRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("New Chat"))
                .andExpect(jsonPath("$.isArchived").value(false));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldGetChatsSuccessfully() throws Exception {
        // Arrange
        Page<ChatResponse> chatPage = new PageImpl<>(List.of(testChatResponse), PageRequest.of(0, 20), 1);

        when(userRepository.findByEmailAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(testUser));
        when(chatService.getUserChats(eq(userId), any())).thenReturn(chatPage);

        // Act & Assert
        mockMvc.perform(get("/chats")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].title").value("Test Chat"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldGetChatByIdSuccessfully() throws Exception {
        // Arrange
        when(userRepository.findByEmailAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(testUser));
        when(chatService.getChatById(chatId, userId)).thenReturn(testChatResponse);

        // Act & Assert
        mockMvc.perform(get("/chats/{chatId}", chatId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Test Chat"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldArchiveChatSuccessfully() throws Exception {
        // Arrange
        when(userRepository.findByEmailAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(testUser));
        doNothing().when(chatService).archiveChat(chatId, userId);

        // Act & Assert
        mockMvc.perform(post("/chats/{chatId}/archive", chatId)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldDeleteChatSuccessfully() throws Exception {
        // Arrange
        when(userRepository.findByEmailAndDeletedAtIsNull(userEmail)).thenReturn(Optional.of(testUser));
        doNothing().when(chatService).deleteChat(chatId, userId);

        // Act & Assert
        mockMvc.perform(delete("/chats/{chatId}", chatId)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/chats"))
                .andExpect(status().isUnauthorized());
    }
}

