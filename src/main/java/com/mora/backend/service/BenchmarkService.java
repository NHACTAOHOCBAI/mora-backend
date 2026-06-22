package com.mora.backend.service;

import com.mora.backend.model.dto.request.BenchmarkQuestionRequest;
import com.mora.backend.model.dto.request.BenchmarkRunRequest;
import com.mora.backend.model.dto.response.BenchmarkQuestionResponse;
import com.mora.backend.model.dto.response.BenchmarkRunResponse;

import com.mora.backend.model.dto.response.PageResponse;

import java.util.List;

public interface BenchmarkService {
    // Benchmark Questions (Golden Dataset) CRUD
    BenchmarkQuestionResponse createQuestion(BenchmarkQuestionRequest request);
    PageResponse<BenchmarkQuestionResponse> getAllQuestions(int page, int limit, String search, String sortBy, String sortOrder);
    BenchmarkQuestionResponse updateQuestion(Long id, BenchmarkQuestionRequest request);
    void deleteQuestion(Long id);

    // Benchmark Run
    BenchmarkRunResponse runBenchmark(BenchmarkRunRequest request);
    PageResponse<BenchmarkRunResponse> getAllRuns(int page, int limit, String search, String sortBy, String sortOrder);
    BenchmarkRunResponse getRunById(Long id);
    void deleteRun(Long id);
}
