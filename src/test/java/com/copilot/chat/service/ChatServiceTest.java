package com.copilot.chat.service;

import com.copilot.chat.dto.request.CreateChatRequest;
import com.copilot.chat.dto.response.ChatResponse;
import com.copilot.chat.model.Chat;
import com.copilot.chat.repository.ChatRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private ChatService chatService;

    private UUID userId;
    private UUID chatId;
    private Chat testChat;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        chatId = UUID.randomUUID();
        testChat = Chat.builder()
                .id(chatId)
                .userId(userId)
                .title("Test Chat")
                .isArchived(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deletedAt(null)
                .build();
    }

    @Test
    void shouldCreateChatWhenValidDataProvided() {
        // Arrange
        CreateChatRequest request = new CreateChatRequest("New Chat");
        Chat savedChat = Chat.builder()
                .id(chatId)
                .userId(userId)
                .title("New Chat")
                .isArchived(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(chatRepository.save(any(Chat.class))).thenReturn(savedChat);
        when(messageRepository.countByChatId(chatId)).thenReturn(0L);

        // Act
        ChatResponse response = chatService.createChat(userId, request);

        // Assert
        assertNotNull(response);
        assertEquals("New Chat", response.title());
        assertEquals(false, response.isArchived());
        assertEquals(0L, response.messageCount());

        verify(chatRepository, times(1)).save(any(Chat.class));
        verify(messageRepository, times(1)).countByChatId(chatId);
    }

    @Test
    void shouldCreateChatWithDefaultTitleWhenTitleIsNull() {
        // Arrange
        CreateChatRequest request = new CreateChatRequest(null);
        Chat savedChat = Chat.builder()
                .id(chatId)
                .userId(userId)
                .title("Новый чат")
                .isArchived(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(chatRepository.save(any(Chat.class))).thenReturn(savedChat);
        when(messageRepository.countByChatId(chatId)).thenReturn(0L);

        // Act
        ChatResponse response = chatService.createChat(userId, request);

        // Assert
        assertNotNull(response);
        assertEquals("Новый чат", response.title());
        verify(chatRepository, times(1)).save(any(Chat.class));
    }

    @Test
    void shouldCreateChatWithDefaultTitleWhenTitleIsEmpty() {
        // Arrange
        CreateChatRequest request = new CreateChatRequest("");
        Chat savedChat = Chat.builder()
                .id(chatId)
                .userId(userId)
                .title("Новый чат")
                .isArchived(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(chatRepository.save(any(Chat.class))).thenReturn(savedChat);
        when(messageRepository.countByChatId(chatId)).thenReturn(0L);

        // Act
        ChatResponse response = chatService.createChat(userId, request);

        // Assert
        assertNotNull(response);
        assertEquals("Новый чат", response.title());
        verify(chatRepository, times(1)).save(any(Chat.class));
    }

    @Test
    void shouldGetUserChatsWithPagination() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Page<Chat> chatPage = new PageImpl<>(List.of(testChat), pageable, 1);

        when(chatRepository.findAllByUserIdAndNotDeleted(userId, pageable)).thenReturn(chatPage);
        when(messageRepository.countByChatId(chatId)).thenReturn(5L);

        // Act
        Page<ChatResponse> result = chatService.getUserChats(userId, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Test Chat", result.getContent().get(0).title());
        assertEquals(5L, result.getContent().get(0).messageCount());

        verify(chatRepository, times(1)).findAllByUserIdAndNotDeleted(userId, pageable);
        verify(messageRepository, times(1)).countByChatId(chatId);
    }

    @Test
    void shouldGetChatByIdWhenChatExists() {
        // Arrange
        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.of(testChat));
        when(messageRepository.countByChatId(chatId)).thenReturn(10L);

        // Act
        ChatResponse response = chatService.getChatById(chatId, userId);

        // Assert
        assertNotNull(response);
        assertEquals(chatId, response.id());
        assertEquals("Test Chat", response.title());
        assertEquals(10L, response.messageCount());

        verify(chatRepository, times(1)).findByIdAndUserId(chatId, userId);
        verify(messageRepository, times(1)).countByChatId(chatId);
    }

    @Test
    void shouldThrowExceptionWhenChatNotFound() {
        // Arrange
        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> chatService.getChatById(chatId, userId));
        
        assertEquals("Чат не найден или у вас нет доступа", exception.getMessage());
        verify(chatRepository, times(1)).findByIdAndUserId(chatId, userId);
        verify(messageRepository, never()).countByChatId(any());
    }

    @Test
    void shouldArchiveChatWhenChatExists() {
        // Arrange
        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.of(testChat));
        when(chatRepository.save(any(Chat.class))).thenReturn(testChat);

        // Act
        chatService.archiveChat(chatId, userId);

        // Assert
        assertTrue(testChat.getIsArchived());
        verify(chatRepository, times(1)).findByIdAndUserId(chatId, userId);
        verify(chatRepository, times(1)).save(testChat);
    }

    @Test
    void shouldThrowExceptionWhenArchivingNonExistentChat() {
        // Arrange
        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> chatService.archiveChat(chatId, userId));
        
        assertEquals("Чат не найден или у вас нет доступа", exception.getMessage());
        verify(chatRepository, times(1)).findByIdAndUserId(chatId, userId);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void shouldDeleteChatWhenChatExists() {
        // Arrange
        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.of(testChat));
        when(chatRepository.save(any(Chat.class))).thenReturn(testChat);

        // Act
        chatService.deleteChat(chatId, userId);

        // Assert
        assertNotNull(testChat.getDeletedAt());
        verify(chatRepository, times(1)).findByIdAndUserId(chatId, userId);
        verify(chatRepository, times(1)).save(testChat);
    }

    @Test
    void shouldThrowExceptionWhenDeletingNonExistentChat() {
        // Arrange
        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> chatService.deleteChat(chatId, userId));
        
        assertEquals("Чат не найден или у вас нет доступа", exception.getMessage());
        verify(chatRepository, times(1)).findByIdAndUserId(chatId, userId);
        verify(chatRepository, never()).save(any());
    }
}

