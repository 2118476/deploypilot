package com.deploypilot.repository;

import com.deploypilot.model.CopilotMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CopilotMessageRepository extends JpaRepository<CopilotMessage, Long> {
    List<CopilotMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
    List<CopilotMessage> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);
    long countByConversationId(Long conversationId);
    void deleteByConversationId(Long conversationId);
}
