package com.deploypilot.repository;

import com.deploypilot.model.ErrorReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ErrorReportRepository extends JpaRepository<ErrorReport, Long> {
    List<ErrorReport> findByUserIdOrderByCreatedAtDesc(Long userId);
}
