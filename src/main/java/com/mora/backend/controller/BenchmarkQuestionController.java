package com.mora.backend.controller;

import com.mora.backend.model.dto.request.BenchmarkQuestionRequest;
import com.mora.backend.model.dto.response.ApiResponse;
import com.mora.backend.model.dto.response.BenchmarkQuestionResponse;
import com.mora.backend.service.BenchmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mora.backend.model.dto.response.PageResponse;
import java.util.List;

@RestController
@RequestMapping("/api/benchmark-questions")
@RequiredArgsConstructor
@Tag(name = "Benchmark Questions API", description = "Các API quản lý tập dữ liệu kiểm thử (Golden Dataset)")
public class BenchmarkQuestionController {

    private final BenchmarkService benchmarkService;

    @PostMapping
    @Operation(summary = "Tạo một câu hỏi kiểm thử mới")
    public ResponseEntity<ApiResponse<BenchmarkQuestionResponse>> createQuestion(
            @Valid @RequestBody BenchmarkQuestionRequest request) {
        BenchmarkQuestionResponse response = benchmarkService.createQuestion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<BenchmarkQuestionResponse>builder()
                        .message("Tạo câu hỏi kiểm thử thành công")
                        .result(response)
                        .build()
        );
    }

    @GetMapping
    @Operation(summary = "Lấy tất cả câu hỏi kiểm thử trong Golden Dataset")
    public ResponseEntity<ApiResponse<PageResponse<BenchmarkQuestionResponse>>> getAllQuestions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortOrder
    ) {
        PageResponse<BenchmarkQuestionResponse> response = benchmarkService.getAllQuestions(page, limit, search, sortBy, sortOrder);
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<BenchmarkQuestionResponse>>builder()
                        .message("Lấy danh sách câu hỏi kiểm thử thành công")
                        .result(response)
                        .build()
        );
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật một câu hỏi kiểm thử")
    public ResponseEntity<ApiResponse<BenchmarkQuestionResponse>> updateQuestion(
            @PathVariable("id") Long id,
            @Valid @RequestBody BenchmarkQuestionRequest request) {
        BenchmarkQuestionResponse response = benchmarkService.updateQuestion(id, request);
        return ResponseEntity.ok(
                ApiResponse.<BenchmarkQuestionResponse>builder()
                        .message("Cập nhật câu hỏi kiểm thử thành công")
                        .result(response)
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa một câu hỏi kiểm thử")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(@PathVariable("id") Long id) {
        benchmarkService.deleteQuestion(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("Xóa câu hỏi kiểm thử thành công")
                        .build()
        );
    }
}
