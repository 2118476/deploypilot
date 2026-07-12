package com.deploypilot.repository;

import com.deploypilot.model.NotificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTokenRepository extends JpaRepository<NotificationToken, Long> {
    List<NotificationToken> findByUserId(Long userId);
    Optional<NotificationToken> findByToken(String token);
}
