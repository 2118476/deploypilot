package com.deploypilot.repository;

import com.deploypilot.model.Guide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GuideRepository extends JpaRepository<Guide, Long> {
    List<Guide> findByCategoryIdOrderBySortOrderAsc(Long categoryId);
    Optional<Guide> findBySlug(String slug);
    List<Guide> findByTitleContainingIgnoreCase(String query);
}
