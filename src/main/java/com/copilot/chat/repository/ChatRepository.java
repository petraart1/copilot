package com.copilot.chat.repository;

import com.copilot.chat.model.Chat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRepository extends JpaRepository<Chat, UUID> {

    @Query("SELECT c FROM Chat c WHERE c.userId = :userId AND c.deletedAt IS NULL ORDER BY c.updatedAt DESC")
    Page<Chat> findAllByUserIdAndNotDeleted(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT c FROM Chat c WHERE c.userId = :userId AND c.deletedAt IS NULL ORDER BY c.updatedAt DESC")
    List<Chat> findAllByUserIdAndNotDeleted(@Param("userId") UUID userId);

    @Query("SELECT c FROM Chat c WHERE c.id = :chatId AND c.userId = :userId AND c.deletedAt IS NULL")
    Optional<Chat> findByIdAndUserId(@Param("chatId") UUID chatId, @Param("userId") UUID userId);

    @Query("SELECT c FROM Chat c WHERE c.userId = :userId AND c.isArchived = true AND c.deletedAt IS NULL ORDER BY c.updatedAt DESC")
    Page<Chat> findArchivedByUserId(@Param("userId") UUID userId, Pageable pageable);
}
