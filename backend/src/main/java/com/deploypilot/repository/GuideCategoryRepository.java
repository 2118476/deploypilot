package com.deploypilot.repository;

import com.deploypilot.model.GuideCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface GuideCategoryRepository extends JpaRepository<GuideCategory, Long> {
    Optional<GuideCategory> findBySlug(String slug);
    List<GuideCategory> findAllByOrderBySortOrderAsc();
}
