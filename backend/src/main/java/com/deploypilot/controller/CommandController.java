package com.deploypilot.controller;

import com.deploypilot.dto.ApiResponse;
import com.deploypilot.model.CommandSnippet;
import com.deploypilot.service.CommandService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/commands")
public class CommandController {

    private final CommandService commandService;

    public CommandController(CommandService commandService) { this.commandService = commandService; }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CommandSnippet>>> getCommands(@RequestParam(required = false) String category) {
        return ResponseEntity.ok(ApiResponse.ok(commandService.getByCategory(category)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<CommandSnippet>>> search(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.ok(commandService.search(q)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CommandSnippet>> getCommand(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(commandService.getById(id)));
    }
}
