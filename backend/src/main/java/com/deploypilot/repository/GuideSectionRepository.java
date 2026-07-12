package com.deploypilot.repository;

import com.deploypilot.model.GuideSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GuideSectionRepository extends JpaRepository<GuideSection, Long> {
    List<GuideSection> findByGuideIdOrderBySortOrderAsc(Long guideId);
}
