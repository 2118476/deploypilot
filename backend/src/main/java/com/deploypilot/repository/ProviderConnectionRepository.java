package com.deploypilot.repository;

import com.deploypilot.model.ProviderConnection;
import com.deploypilot.model.enums.ProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderConnectionRepository extends JpaRepository<ProviderConnection, Long> {
    List<ProviderConnection> findByUserIdOrderByProviderAsc(Long userId);
    Optional<ProviderConnection> findByUserIdAndProvider(Long userId, ProviderType provider);
    Optional<ProviderConnection> findByIdAndUserId(Long id, Long userId);
}
