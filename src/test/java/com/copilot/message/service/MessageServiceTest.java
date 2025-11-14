package com.copilot.message.service;

import com.copilot.agent.dto.response.ActionResponse;
import com.copilot.agent.dto.response.ExecuteTaskResponse;
import com.copilot.chat.model.Chat;
import com.copilot.chat.repository.ChatRepository;
import com.copilot.llm.service.AgentService;
import com.copilot.message.dto.request.SendMessageRequest;
import com.copilot.message.dto.response.MessageResponse;
import com.copilot.message.model.Message;
import com.copilot.message.repository.MessageRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private AgentService agentService;

    @InjectMocks
    private MessageService messageService;

    private UUID userId;
    private UUID chatId;
    private String userEmail;
    private Chat testChat;
    private Message testMessage;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        chatId = UUID.randomUUID();
        userEmail = "test@example.com";
        
        testChat = Chat.builder()
                .id(chatId)
                .userId(userId)
                .title("Test Chat")
                .isArchived(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        testMessage = Message.builder()
                .id(UUID.randomUUID())
                .chatId(chatId)
                .userMessage("Hello")
                .aiResponse("Hi there!")
                .responseType("agent_response")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void shouldSendMessageSuccessfully() {
        // Arrange
        SendMessageRequest request = new SendMessageRequest("Hello");
        ExecuteTaskResponse agentResponse = new ExecuteTaskResponse(
                "success",
                "Hi there!",
                List.of(new ActionResponse("test_tool", "completed", Map.of("result", "ok")))
        );

        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.of(testChat));
        when(agentService.executeTask("Hello", userEmail)).thenReturn(agentResponse);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            msg.setId(UUID.randomUUID());
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(chatRepository.save(any(Chat.class))).thenReturn(testChat);

        // Act
        MessageResponse response = messageService.sendMessage(chatId, userId, userEmail, request);

        // Assert
        assertNotNull(response);
        assertEquals("Hello", response.userMessage());
        assertEquals("Hi there!", response.aiResponse());
        assertEquals("agent_response", response.responseType());
        assertNotNull(response.contextData());
        assertTrue(response.contextData().containsKey("actions"));

        verify(chatRepository, times(1)).findByIdAndUserId(chatId, userId);
        verify(agentService, times(1)).executeTask("Hello", userEmail);
        verify(messageRepository, times(1)).save(any(Message.class));
        verify(chatRepository, times(1)).save(any(Chat.class));
    }

    @Test
    void shouldHandleAgentErrorGracefully() {
        // Arrange
        SendMessageRequest request = new SendMessageRequest("Hello");
        RuntimeException agentError = new RuntimeException("Agent service error");

        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.of(testChat));
        when(agentService.executeTask("Hello", userEmail)).thenThrow(agentError);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            msg.setId(UUID.randomUUID());
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(chatRepository.save(any(Chat.class))).thenReturn(testChat);

        // Act
        MessageResponse response = messageService.sendMessage(chatId, userId, userEmail, request);

        // Assert
        assertNotNull(response);
        assertEquals("Hello", response.userMessage());
        assertNotNull(response.aiResponse());
        assertTrue(response.aiResponse().contains("Произошла ошибка"));
        assertEquals("error", response.responseType());

        verify(chatRepository, times(1)).findByIdAndUserId(chatId, userId);
        verify(agentService, times(1)).executeTask("Hello", userEmail);
        verify(messageRepository, times(1)).save(any(Message.class));
        verify(chatRepository, times(1)).save(any(Chat.class));
    }

    @Test
    void shouldThrowExceptionWhenChatNotFound() {
        // Arrange
        SendMessageRequest request = new SendMessageRequest("Hello");
        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> messageService.sendMessage(chatId, userId, userEmail, request));
        
        assertEquals("Чат не найден или у вас нет доступа", exception.getMessage());
        verify(chatRepository, times(1)).findByIdAndUserId(chatId, userId);
        verify(agentService, never()).executeTask(any(), any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void shouldGetChatMessagesWithPagination() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 50);
        Page<Message> messagePage = new PageImpl<>(List.of(testMessage), pageable, 1);

        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.of(testChat));
        when(messageRepository.findAllByChatIdOrderByCreatedAtDesc(chatId, pageable)).thenReturn(messagePage);

        // Act
        Page<MessageResponse> result = messageService.getChatMessages(chatId, userId, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Hello", result.getContent().get(0).userMessage());
        assertEquals("Hi there!", result.getContent().get(0).aiResponse());

        verify(chatRepository, times(1)).findByIdAndUserId(chatId, userId);
        verify(messageRepository, times(1)).findAllByChatIdOrderByCreatedAtDesc(chatId, pageable);
    }

    @Test
    void shouldThrowExceptionWhenGettingMessagesForNonExistentChat() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 50);
        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> messageService.getChatMessages(chatId, userId, pageable));
        
        assertEquals("Чат не найден или у вас нет доступа", exception.getMessage());
        verify(chatRepository, times(1)).findByIdAndUserId(chatId, userId);
        verify(messageRepository, never()).findAllByChatIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void shouldUpdateChatTimestampWhenSendingMessage() {
        // Arrange
        SendMessageRequest request = new SendMessageRequest("Hello");
        ExecuteTaskResponse agentResponse = new ExecuteTaskResponse("success", "Response", List.of());

        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.of(testChat));
        when(agentService.executeTask("Hello", userEmail)).thenReturn(agentResponse);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            msg.setId(UUID.randomUUID());
            msg.setCreatedAt(LocalDateTime.now());
            return msg;
        });
        when(chatRepository.save(any(Chat.class))).thenAnswer(invocation -> {
            Chat chat = invocation.getArgument(0);
            chat.setUpdatedAt(LocalDateTime.now());
            return chat;
        });

        // Act
        messageService.sendMessage(chatId, userId, userEmail, request);

        // Assert
        verify(chatRepository, times(1)).save(any(Chat.class));
    }
}

