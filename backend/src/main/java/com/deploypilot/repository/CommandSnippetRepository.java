package com.deploypilot.repository;

import com.deploypilot.model.CommandSnippet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CommandSnippetRepository extends JpaRepository<CommandSnippet, Long> {
    List<CommandSnippet> findByCategory(String category);
    List<CommandSnippet> findByTitleContainingIgnoreCaseOrCommandContainingIgnoreCase(String q1, String q2);
}
