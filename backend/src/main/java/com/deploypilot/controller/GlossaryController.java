package com.deploypilot.controller;

import com.deploypilot.dto.ApiResponse;
import com.deploypilot.dto.GlossaryTermResponse;
import com.deploypilot.service.GlossaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/glossary")
public class GlossaryController {

    private final GlossaryService glossaryService;

    public GlossaryController(GlossaryService glossaryService) { this.glossaryService = glossaryService; }

    @GetMapping
    public ResponseEntity<ApiResponse<List<GlossaryTermResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(glossaryService.getAllTerms()));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<GlossaryTermResponse>>> search(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.ok(glossaryService.search(q)));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<GlossaryTermResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok(glossaryService.getBySlug(slug)));
    }
}
