package com.copilot.message.repository;

import com.copilot.message.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Репозиторий для работы с сообщениями
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    /**
     * Находит все сообщения чата с пагинацией (от новых к старым)
     */
    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId ORDER BY m.createdAt DESC")
    Page<Message> findAllByChatIdOrderByCreatedAtDesc(@Param("chatId") UUID chatId, Pageable pageable);

    /**
     * Находит все сообщения чата (без пагинации, от новых к старым)
     */
    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId ORDER BY m.createdAt DESC")
    java.util.List<Message> findAllByChatIdOrderByCreatedAtDesc(@Param("chatId") UUID chatId);

    /**
     * Находит все сообщения чата (без пагинации, от старых к новым)
     */
    @Query("SELECT m FROM Message m WHERE m.chatId = :chatId ORDER BY m.createdAt ASC")
    java.util.List<Message> findAllByChatIdOrderByCreatedAtAsc(@Param("chatId") UUID chatId);

    /**
     * Подсчитывает количество сообщений в чате
     */
    long countByChatId(UUID chatId);
}


