package com.copilot.chat.service;

import com.copilot.chat.dto.request.CreateChatRequest;
import com.copilot.chat.dto.response.ChatResponse;
import com.copilot.chat.model.Chat;
import com.copilot.chat.repository.ChatRepository;
import com.copilot.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;

    @Transactional
    public ChatResponse createChat(UUID userId, CreateChatRequest request) {
        log.info("Создание нового чата для пользователя: {}", userId);

        Chat chat = Chat.builder()
                .userId(userId)
                .title(request.title() != null && !request.title().isEmpty() 
                        ? request.title() 
                        : "Новый чат")
                .isArchived(false)
                .build();

        Chat saved = chatRepository.save(chat);
        log.info("Чат создан: {}", saved.getId());

        return toChatResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ChatResponse> getUserChats(UUID userId, Pageable pageable) {
        log.debug("Получение чатов пользователя: {}", userId);
        Page<Chat> chats = chatRepository.findAllByUserIdAndNotDeleted(userId, pageable);
        return chats.map(this::toChatResponse);
    }

    @Transactional(readOnly = true)
    public List<ChatResponse> getAllUserChats(UUID userId) {
        log.debug("Получение всех чатов пользователя: {}", userId);
        List<Chat> chats = chatRepository.findAllByUserIdAndNotDeleted(userId);
        return chats.stream()
                .map(this::toChatResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ChatResponse getChatById(UUID chatId, UUID userId) {
        log.debug("Получение чата {} для пользователя {}", chatId, userId);
        Chat chat = chatRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new RuntimeException("Чат не найден или у вас нет доступа"));
        return toChatResponse(chat);
    }

    @Transactional
    public void archiveChat(UUID chatId, UUID userId) {
        log.info("Архивирование чата {} пользователем {}", chatId, userId);
        Chat chat = chatRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new RuntimeException("Чат не найден или у вас нет доступа"));
        chat.setIsArchived(true);
        chatRepository.save(chat);
        log.info("Чат {} архивирован", chatId);
    }

    @Transactional
    public void deleteChat(UUID chatId, UUID userId) {
        log.info("Удаление чата {} пользователем {}", chatId, userId);
        Chat chat = chatRepository.findByIdAndUserId(chatId, userId)
                .orElseThrow(() -> new RuntimeException("Чат не найден или у вас нет доступа"));
        chat.setDeletedAt(java.time.LocalDateTime.now());
        chatRepository.save(chat);
        log.info("Чат {} удален", chatId);
    }

    private ChatResponse toChatResponse(Chat chat) {
        long messageCount = messageRepository.countByChatId(chat.getId());
        return new ChatResponse(
                chat.getId(),
                chat.getTitle(),
                chat.getIsArchived(),
                chat.getCreatedAt(),
                chat.getUpdatedAt(),
                messageCount
        );
    }
}


