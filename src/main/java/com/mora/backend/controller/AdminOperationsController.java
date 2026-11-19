package com.mora.backend.controller;

import com.mora.backend.model.dto.response.AdminOperationsResponses;
import com.mora.backend.model.dto.response.ApiResponse;
import com.mora.backend.model.dto.response.PageResponse;
import com.mora.backend.model.entity.DocumentStatus;
import com.mora.backend.service.AdminOperationsService;
import com.mora.backend.service.AiSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOperationsController {
    private final AdminOperationsService service;
    private final AiSettingsService aiSettings;

    @GetMapping("/ai-settings") public ResponseEntity<ApiResponse<AiSettingsService.SettingsView>> aiSettings() { return ok(aiSettings.get()); }
    @PutMapping("/ai-settings") public ResponseEntity<ApiResponse<AiSettingsService.SettingsView>> updateAiSettings(@RequestBody AiSettingsService.UpdateRequest request) { return ok(aiSettings.update(request)); }

    @GetMapping("/dashboard") public ResponseEntity<ApiResponse<AdminOperationsResponses.Dashboard>> dashboard(@RequestParam(defaultValue = "30") int range) {
        return ok(service.dashboard(range));
    }
    @GetMapping("/spaces") public ResponseEntity<ApiResponse<PageResponse<AdminOperationsResponses.SpaceSummary>>> spaces(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int limit, @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "lastActivityAt") String sortBy, @RequestParam(defaultValue = "DESC") String sortOrder) {
        return ok(service.spaces(page, limit, search, sortBy, sortOrder));
    }
    @GetMapping("/spaces/{id}") public ResponseEntity<ApiResponse<AdminOperationsResponses.SpaceDetail>> space(@PathVariable Long id) { return ok(service.spaceDetail(id)); }
    @GetMapping("/documents") public ResponseEntity<ApiResponse<PageResponse<AdminOperationsResponses.DocumentSummary>>> documents(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int limit, @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "createdAt") String sortBy, @RequestParam(defaultValue = "DESC") String sortOrder,
            @RequestParam(required = false) DocumentStatus status, @RequestParam(required = false) Long spaceId, @RequestParam(required = false) Long uploaderId) {
        return ok(service.documents(page, limit, search, sortBy, sortOrder, status, spaceId, uploaderId));
    }
    @GetMapping("/documents/{id}") public ResponseEntity<ApiResponse<AdminOperationsResponses.DocumentSummary>> document(@PathVariable Long id) { return ok(service.documentDetail(id)); }
    @GetMapping("/users/{id}") public ResponseEntity<ApiResponse<AdminOperationsResponses.UserDetail>> user(@PathVariable Long id) { return ok(service.userDetail(id)); }
    private <T> ResponseEntity<ApiResponse<T>> ok(T result) { return ResponseEntity.ok(ApiResponse.<T>builder().result(result).build()); }
}
