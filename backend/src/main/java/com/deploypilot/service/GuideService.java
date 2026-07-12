package com.deploypilot.service;

import com.deploypilot.dto.*;
import com.deploypilot.model.GuideCategory;
import com.deploypilot.model.Guide;
import com.deploypilot.model.GuideSection;
import com.deploypilot.repository.GuideCategoryRepository;
import com.deploypilot.repository.GuideRepository;
import com.deploypilot.repository.GuideSectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class GuideService {

    private final GuideCategoryRepository categoryRepo;
    private final GuideRepository guideRepo;
    private final GuideSectionRepository sectionRepo;

    public GuideService(GuideCategoryRepository categoryRepo, GuideRepository guideRepo,
                        GuideSectionRepository sectionRepo) {
        this.categoryRepo = categoryRepo;
        this.guideRepo = guideRepo;
        this.sectionRepo = sectionRepo;
    }

    @Transactional(readOnly = true)
    public List<GuideCategoryResponse> getCategories() {
        return categoryRepo.findAllByOrderBySortOrderAsc().stream()
                .map(c -> new GuideCategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription(), c.getIcon(), c.getSortOrder()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GuideResponse> getGuidesByCategory(String categorySlug) {
        GuideCategory cat = categoryRepo.findBySlug(categorySlug).orElse(null);
        if (cat == null) return List.of();
        return guideRepo.findByCategoryIdOrderBySortOrderAsc(cat.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GuideResponse getGuide(String slug) {
        Guide guide = guideRepo.findBySlug(slug)
                .orElseThrow(() -> new com.deploypilot.exception.ResourceNotFoundException("Guide not found"));
        return toResponse(guide);
    }

    @Transactional(readOnly = true)
    public List<GuideResponse> searchGuides(String query) {
        return guideRepo.findByTitleContainingIgnoreCase(query).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private GuideResponse toResponse(Guide g) {
        GuideResponse r = new GuideResponse();
        r.setId(g.getId());
        r.setCategoryId(g.getCategoryId());
        r.setTitle(g.getTitle());
        r.setSlug(g.getSlug());
        r.setDescription(g.getDescription());
        r.setContent(g.getContent());
        r.setDifficulty(g.getDifficulty());
        r.setCreatedAt(g.getCreatedAt());
        List<GuideSection> sections = sectionRepo.findByGuideIdOrderBySortOrderAsc(g.getId());
        r.setSections(sections.stream()
                .map(s -> new GuideSectionDto(s.getId(), s.getGuideId(), s.getTitle(), s.getContent(), s.getSortOrder()))
                .collect(Collectors.toList()));
        return r;
    }
}
