package com.deploypilot.service;

import com.deploypilot.dto.GlossaryTermResponse;
import com.deploypilot.model.GlossaryTerm;
import com.deploypilot.repository.GlossaryTermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GlossaryService {

    private final GlossaryTermRepository repo;

    public GlossaryService(GlossaryTermRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public List<GlossaryTermResponse> getAllTerms() {
        return repo.findAllByOrderByTermAsc().stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GlossaryTermResponse> search(String query) {
        return repo.findByTermContainingIgnoreCase(query).stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GlossaryTermResponse getBySlug(String slug) {
        GlossaryTerm t = repo.findBySlug(slug)
                .orElseThrow(() -> new com.deploypilot.exception.ResourceNotFoundException("Term not found"));
        return toResponse(t);
    }

    private GlossaryTermResponse toResponse(GlossaryTerm t) {
        GlossaryTermResponse r = new GlossaryTermResponse();
        r.setId(t.getId()); r.setTerm(t.getTerm()); r.setSlug(t.getSlug());
        r.setDefinition(t.getDefinition()); r.setExample(t.getExample());
        r.setCategory(t.getCategory()); r.setRelatedTerms(t.getRelatedTerms());
        return r;
    }
}
