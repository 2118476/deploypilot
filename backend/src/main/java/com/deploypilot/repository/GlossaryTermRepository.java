package com.deploypilot.repository;

import com.deploypilot.model.GlossaryTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GlossaryTermRepository extends JpaRepository<GlossaryTerm, Long> {
    Optional<GlossaryTerm> findBySlug(String slug);
    List<GlossaryTerm> findByTermContainingIgnoreCase(String query);
    List<GlossaryTerm> findAllByOrderByTermAsc();
}
