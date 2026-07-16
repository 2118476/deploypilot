package com.deploypilot.repository;

import com.deploypilot.model.CopilotConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CopilotConversationRepository extends JpaRepository<CopilotConversation, Long> {
    Optional<CopilotConversation> findByUserIdAndProjectId(Long userId, Long projectId);
}
