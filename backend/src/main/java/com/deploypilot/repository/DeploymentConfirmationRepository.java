package com.deploypilot.repository;

import com.deploypilot.model.DeploymentConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeploymentConfirmationRepository extends JpaRepository<DeploymentConfirmation, Long> {
    Optional<DeploymentConfirmation> findByNonce(String nonce);
}
