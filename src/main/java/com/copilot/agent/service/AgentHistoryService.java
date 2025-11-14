package com.copilot.agent.service;

import com.copilot.agent.dto.response.AgentActionResponse;
import com.copilot.agent.model.AgentAction;
import com.copilot.agent.repository.AgentActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentHistoryService {

    private final AgentActionRepository agentActionRepository;

    @Transactional(readOnly = true)
    public Page<AgentActionResponse> getUserHistory(UUID userId, Pageable pageable) {
        log.debug("Получение истории действий пользователя: {}", userId);
        Page<AgentAction> actions = agentActionRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        return actions.map(this::toAgentActionResponse);
    }

    @Transactional(readOnly = true)
    public Page<AgentActionResponse> getUserHistoryWithFilters(
            UUID userId,
            String status,
            String actionType,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {
        log.debug("Получение истории действий пользователя {} с фильтрами: status={}, actionType={}, from={}, to={}",
                userId, status, actionType, from, to);

        Page<AgentAction> actions = agentActionRepository.findByUserIdWithFilters(
                userId, status, actionType, from, to, pageable);
        return actions.map(this::toAgentActionResponse);
    }

    private AgentActionResponse toAgentActionResponse(AgentAction action) {
        return new AgentActionResponse(
                action.getId(),
                action.getChatId(),
                action.getActionType(),
                action.getInputData(),
                action.getOutputData(),
                action.getStatus(),
                action.getErrorMessage(),
                action.getDurationMs(),
                action.getCreatedAt()
        );
    }
}

