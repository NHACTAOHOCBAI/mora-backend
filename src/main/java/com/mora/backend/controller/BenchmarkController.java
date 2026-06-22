package com.mora.backend.controller;

import com.mora.backend.model.dto.request.BenchmarkRunRequest;
import com.mora.backend.model.dto.response.ApiResponse;
import com.mora.backend.model.dto.response.BenchmarkRunResponse;
import com.mora.backend.service.BenchmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mora.backend.model.dto.response.PageResponse;
import java.util.List;

@RestController
@RequestMapping("/api/benchmarks")
@RequiredArgsConstructor
@Tag(name = "Benchmark Execution API", description = "Các API chạy đánh giá và đối chiếu hiệu năng RAG (Ragas)")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    @PostMapping("/run")
    @Operation(summary = "Kích hoạt chạy benchmark cho một hướng tiếp cận mới")
    public ResponseEntity<ApiResponse<BenchmarkRunResponse>> runBenchmark(
            @Valid @RequestBody BenchmarkRunRequest request) {
        BenchmarkRunResponse response = benchmarkService.runBenchmark(request);
        return ResponseEntity.ok(
                ApiResponse.<BenchmarkRunResponse>builder()
                        .message("Chạy benchmark thành công")
                        .result(response)
                        .build()
        );
    }

    @GetMapping("/history")
    @Operation(summary = "Lấy lịch sử các lần chạy benchmark")
    public ResponseEntity<ApiResponse<PageResponse<BenchmarkRunResponse>>> getHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortOrder
    ) {
        PageResponse<BenchmarkRunResponse> response = benchmarkService.getAllRuns(page, limit, search, sortBy, sortOrder);
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<BenchmarkRunResponse>>builder()
                        .message("Lấy lịch sử benchmark thành công")
                        .result(response)
                        .build()
        );
    }

    @GetMapping("/history/{id}")
    @Operation(summary = "Lấy chi tiết một lượt chạy benchmark kèm điểm số chi tiết")
    public ResponseEntity<ApiResponse<BenchmarkRunResponse>> getRunDetails(@PathVariable("id") Long id) {
        BenchmarkRunResponse response = benchmarkService.getRunById(id);
        return ResponseEntity.ok(
                ApiResponse.<BenchmarkRunResponse>builder()
                        .message("Lấy chi tiết lượt benchmark thành công")
                        .result(response)
                        .build()
        );
    }

    @DeleteMapping("/history/{id}")
    @Operation(summary = "Xóa lịch sử lượt chạy benchmark")
    public ResponseEntity<ApiResponse<Void>> deleteRun(@PathVariable("id") Long id) {
        benchmarkService.deleteRun(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("Xóa lượt benchmark thành công")
                        .build()
        );
    }
}
