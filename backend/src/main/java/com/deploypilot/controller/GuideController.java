package com.deploypilot.controller;

import com.deploypilot.dto.*;
import com.deploypilot.service.GuideService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/guides")
public class GuideController {

    private final GuideService guideService;

    public GuideController(GuideService guideService) { this.guideService = guideService; }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<GuideCategoryResponse>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok(guideService.getCategories()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GuideResponse>>> getByCategory(@RequestParam String category) {
        return ResponseEntity.ok(ApiResponse.ok(guideService.getGuidesByCategory(category)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<GuideResponse>>> search(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.ok(guideService.searchGuides(q)));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<GuideResponse>> getGuide(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(guideService.getGuide(slug)));
    }
}
