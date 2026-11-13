package com.mora.backend.controller;

import com.mora.backend.model.dto.request.*;
import com.mora.backend.model.dto.response.*;
import com.mora.backend.service.StudySetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
public class StudySetController {
    private final StudySetService service;
    @PostMapping("/api/spaces/{spaceId}/study-sets")
    public ResponseEntity<ApiResponse<StudySetResponse>> create(@PathVariable Long spaceId, @Valid @RequestBody CreateStudySetRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<StudySetResponse>builder().message("Tạo bộ ôn tập thành công").result(service.create(spaceId, body)).build());
    }
    @GetMapping("/api/spaces/{spaceId}/study-sets")
    public ResponseEntity<ApiResponse<List<StudySetResponse>>> list(@PathVariable Long spaceId) { return ResponseEntity.ok(ApiResponse.<List<StudySetResponse>>builder().result(service.list(spaceId)).build()); }
    @GetMapping("/api/study-sets/{id}")
    public ResponseEntity<ApiResponse<StudySetResponse>> get(@PathVariable Long id) { return ResponseEntity.ok(ApiResponse.<StudySetResponse>builder().result(service.get(id)).build()); }
    @PostMapping("/api/study-sets/{id}/quiz/grade")
    public ResponseEntity<ApiResponse<QuizGradeResponse>> grade(@PathVariable Long id, @Valid @RequestBody QuizGradeRequest body) { return ResponseEntity.ok(ApiResponse.<QuizGradeResponse>builder().result(service.grade(id, body)).build()); }
    @DeleteMapping("/api/study-sets/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) { service.delete(id); return ResponseEntity.ok(ApiResponse.<Void>builder().message("Đã xóa bộ ôn tập").build()); }
}
