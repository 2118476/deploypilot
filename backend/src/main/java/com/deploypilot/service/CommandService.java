package com.deploypilot.service;

import com.deploypilot.model.CommandSnippet;
import com.deploypilot.repository.CommandSnippetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class CommandService {
    private final CommandSnippetRepository repo;
    public CommandService(CommandSnippetRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public List<CommandSnippet> getByCategory(String category) {
        return category != null ? repo.findByCategory(category) : repo.findAll();
    }

    @Transactional(readOnly = true)
    public List<CommandSnippet> search(String query) {
        return repo.findByTitleContainingIgnoreCaseOrCommandContainingIgnoreCase(query, query);
    }

    @Transactional(readOnly = true)
    public CommandSnippet getById(Long id) {
        return repo.findById(id).orElseThrow(() -> new com.deploypilot.exception.ResourceNotFoundException("Command not found"));
    }
}
