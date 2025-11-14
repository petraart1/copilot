package com.copilot.agent.repository;

import com.copilot.agent.model.AgentAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;


@Repository
public interface AgentActionRepository extends JpaRepository<AgentAction, UUID> {

    @Query("SELECT a FROM AgentAction a WHERE a.userId = :userId ORDER BY a.createdAt DESC")
    Page<AgentAction> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = "SELECT a FROM AgentAction a WHERE a.userId = :userId ORDER BY a.createdAt DESC")
    List<AgentAction> findTop5ByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, org.springframework.data.domain.Pageable pageable);

    @Query(value = "SELECT a FROM AgentAction a WHERE a.userId = :userId AND a.chatId = :chatId ORDER BY a.createdAt DESC")
    List<AgentAction> findTop5ByUserIdAndChatIdOrderByCreatedAtDesc(@Param("userId") UUID userId, @Param("chatId") UUID chatId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT a FROM AgentAction a WHERE a.userId = :userId AND a.status = :status ORDER BY a.createdAt DESC")
    Page<AgentAction> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") String status, Pageable pageable);

    @Query("SELECT a FROM AgentAction a WHERE a.userId = :userId AND a.actionType = :actionType ORDER BY a.createdAt DESC")
    Page<AgentAction> findByUserIdAndActionType(@Param("userId") UUID userId, @Param("actionType") String actionType, Pageable pageable);

    @Query("SELECT a FROM AgentAction a WHERE a.userId = :userId AND a.createdAt >= :from AND a.createdAt <= :to ORDER BY a.createdAt DESC")
    Page<AgentAction> findByUserIdAndDateRange(
            @Param("userId") UUID userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("SELECT a FROM AgentAction a WHERE a.userId = :userId " +
            "AND (:status IS NULL OR a.status = :status) " +
            "AND (:actionType IS NULL OR a.actionType = :actionType) " +
            "AND (:from IS NULL OR a.createdAt >= :from) " +
            "AND (:to IS NULL OR a.createdAt <= :to) " +
            "ORDER BY a.createdAt DESC")
    Page<AgentAction> findByUserIdWithFilters(
            @Param("userId") UUID userId,
            @Param("status") String status,
            @Param("actionType") String actionType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}

