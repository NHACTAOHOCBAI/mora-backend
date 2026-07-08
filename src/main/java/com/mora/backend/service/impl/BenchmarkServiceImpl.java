package com.mora.backend.service.impl;

import com.mora.backend.client.AiServiceClient;
import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.request.BenchmarkQuestionRequest;
import com.mora.backend.model.dto.request.BenchmarkRunRequest;
import com.mora.backend.model.dto.response.BenchmarkQuestionResponse;
import com.mora.backend.model.dto.response.BenchmarkRunResponse;
import com.mora.backend.model.entity.BenchmarkQuestion;
import com.mora.backend.model.entity.BenchmarkRun;
import com.mora.backend.model.entity.BenchmarkRunDetail;
import com.mora.backend.repository.BenchmarkQuestionRepository;
import com.mora.backend.repository.BenchmarkRunDetailRepository;
import com.mora.backend.repository.BenchmarkRunRepository;
import com.mora.backend.service.BenchmarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mora.backend.model.dto.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BenchmarkServiceImpl implements BenchmarkService {

    private final BenchmarkQuestionRepository questionRepository;
    private final BenchmarkRunRepository runRepository;
    private final BenchmarkRunDetailRepository detailRepository;
    private final AiServiceClient aiServiceClient;

    // --- Benchmark Questions CRUD ---

    @Override
    @Transactional
    public BenchmarkQuestionResponse createQuestion(BenchmarkQuestionRequest request) {
        log.info("Creating a new benchmark question");
        BenchmarkQuestion question = BenchmarkQuestion.builder()
                .question(request.getQuestion())
                .groundTruth(request.getGroundTruth())
                .documentId(request.getDocumentId())
                .build();
        BenchmarkQuestion saved = questionRepository.save(question);
        return mapToQuestionResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BenchmarkQuestionResponse> getAllQuestions(int page, int limit, String search, String sortBy, String sortOrder) {
        log.info("Fetching paginated benchmark questions, page={}, limit={}, search={}", page, limit, search);
        Sort sort = Sort.by(Sort.Direction.fromString(sortOrder.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page - 1, limit, sort);

        Page<BenchmarkQuestion> questionPage = questionRepository.searchQuestions(search, pageable);

        List<BenchmarkQuestionResponse> data = questionPage.getContent().stream()
                .map(this::mapToQuestionResponse)
                .collect(Collectors.toList());

        PageResponse.Pagination pagination = new PageResponse.Pagination(
                questionPage.getTotalElements(),
                page,
                limit
        );
        PageResponse.Meta meta = new PageResponse.Meta(pagination);

        return new PageResponse<>(data, meta);
    }

    @Override
    @Transactional
    public BenchmarkQuestionResponse updateQuestion(Long id, BenchmarkQuestionRequest request) {
        log.info("Updating benchmark question with id: {}", id);
        BenchmarkQuestion question = questionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BENCHMARK_QUESTION_NOT_FOUND));
        question.setQuestion(request.getQuestion());
        question.setGroundTruth(request.getGroundTruth());
        question.setDocumentId(request.getDocumentId());
        BenchmarkQuestion updated = questionRepository.save(question);
        return mapToQuestionResponse(updated);
    }

    @Override
    @Transactional
    public void deleteQuestion(Long id) {
        log.info("Deleting benchmark question with id: {}", id);
        if (!questionRepository.existsById(id)) {
            throw new AppException(ErrorCode.BENCHMARK_QUESTION_NOT_FOUND);
        }
        questionRepository.deleteById(id);
    }

    // --- Benchmark Execution & Runs ---

    @Override
    @Transactional
    public BenchmarkRunResponse runBenchmark(BenchmarkRunRequest request) {
        log.info("Starting benchmark run for approach: {}", request.getApproachName());
        
        List<BenchmarkQuestion> questions = questionRepository.findAll();
        if (questions.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        // Prepare dataset for Python service with mock answers & contexts
        List<Map<String, Object>> dataset = new ArrayList<>();
        for (BenchmarkQuestion q : questions) {
            String context = "Ngữ cảnh thử nghiệm của Mora học tập.";
            List<String> retrievedContexts = new ArrayList<>();
            retrievedContexts.add(context);
            
            long start = System.currentTimeMillis();
            
            // Mock answer generation instead of calling deleted chatWithDocument
            String generatedAnswer = "Đây là câu trả lời thử nghiệm cho câu hỏi: " + q.getQuestion();
            long latencyMs = System.currentTimeMillis() - start;

            log.info("Benchmark processing: question='{}'", q.getQuestion());
            log.info("Retrieved contexts: {}", retrievedContexts);
            log.info("Generated answer: '{}'", generatedAnswer);
            log.info("Latency: {} ms", latencyMs);

            dataset.add(Map.of(
                    "question", q.getQuestion(),
                    "ground_truth", q.getGroundTruth(),
                    "generated_answer", generatedAnswer,
                    "retrieved_contexts", retrievedContexts,
                    "latency_ms", latencyMs
            ));
        }

        // Call Python AI Service
        AiServiceClient.PythonEvaluationResponse evalResponse = aiServiceClient.evaluateBenchmark(
                request.getApproachName(),
                (List<Map<String, String>>) (List<?>) dataset
        );

        // Save Benchmark Run
        BenchmarkRun run = BenchmarkRun.builder()
                .approachName(evalResponse.approachName)
                .ragasFaithfulness(evalResponse.faithfulness)
                .ragasAnswerRelevance(evalResponse.answerRelevance)
                .ragasContextPrecision(evalResponse.contextPrecision)
                .ragasContextRecall(evalResponse.contextRecall)
                .avgLatencyMs(evalResponse.avgLatencyMs)
                .build();

        BenchmarkRun savedRun = runRepository.save(run);

        // Save Details
        List<BenchmarkRunDetail> details = new ArrayList<>();
        if (evalResponse.details != null) {
            for (AiServiceClient.PythonEvaluationResponse.Detail d : evalResponse.details) {
                BenchmarkRunDetail detail = BenchmarkRunDetail.builder()
                        .benchmarkRun(savedRun)
                        .question(d.question)
                        .retrievedContexts(d.retrievedContexts)
                        .generatedAnswer(d.generatedAnswer)
                        .groundTruth(d.groundTruth)
                        .latencyMs(d.latencyMs)
                        .faithfulness(d.faithfulness)
                        .answerRelevance(d.answerRelevance)
                        .contextPrecision(d.contextPrecision)
                        .contextRecall(d.contextRecall)
                        .build();
                details.add(detail);
            }
            detailRepository.saveAll(details);
            savedRun.setDetails(details);
        }

        return mapToRunResponse(savedRun);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BenchmarkRunResponse> getAllRuns(int page, int limit, String search, String sortBy, String sortOrder) {
        log.info("Fetching paginated benchmark runs, page={}, limit={}, search={}", page, limit, search);
        Sort sort = Sort.by(Sort.Direction.fromString(sortOrder.toUpperCase()), sortBy);
        Pageable pageable = PageRequest.of(page - 1, limit, sort);

        Page<BenchmarkRun> runPage = runRepository.searchRuns(search, pageable);

        List<BenchmarkRunResponse> data = runPage.getContent().stream()
                .map(this::mapToRunResponse)
                .collect(Collectors.toList());

        PageResponse.Pagination pagination = new PageResponse.Pagination(
                runPage.getTotalElements(),
                page,
                limit
        );
        PageResponse.Meta meta = new PageResponse.Meta(pagination);

        return new PageResponse<>(data, meta);
    }

    @Override
    @Transactional(readOnly = true)
    public BenchmarkRunResponse getRunById(Long id) {
        log.info("Fetching benchmark run with id: {}", id);
        BenchmarkRun run = runRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BENCHMARK_RUN_NOT_FOUND));
        return mapToRunResponse(run);
    }

    @Override
    @Transactional
    public void deleteRun(Long id) {
        log.info("Deleting benchmark run with id: {}", id);
        if (!runRepository.existsById(id)) {
            throw new AppException(ErrorCode.BENCHMARK_RUN_NOT_FOUND);
        }
        runRepository.deleteById(id);
    }

    // --- Helpers Mappers ---

    private BenchmarkQuestionResponse mapToQuestionResponse(BenchmarkQuestion q) {
        return BenchmarkQuestionResponse.builder()
                .id(q.getId())
                .question(q.getQuestion())
                .groundTruth(q.getGroundTruth())
                .documentId(q.getDocumentId())
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .build();
    }

    private BenchmarkRunResponse mapToRunResponse(BenchmarkRun r) {
        List<BenchmarkRunResponse.DetailResponse> detailResponses = null;
        if (r.getDetails() != null) {
            detailResponses = r.getDetails().stream()
                    .map(d -> BenchmarkRunResponse.DetailResponse.builder()
                            .id(d.getId())
                            .question(d.getQuestion())
                            .retrievedContexts(d.getRetrievedContexts())
                            .generatedAnswer(d.getGeneratedAnswer())
                            .groundTruth(d.getGroundTruth())
                            .latencyMs(d.getLatencyMs())
                            .faithfulness(d.getFaithfulness())
                            .answerRelevance(d.getAnswerRelevance())
                            .contextPrecision(d.getContextPrecision())
                            .contextRecall(d.getContextRecall())
                            .build())
                    .collect(Collectors.toList());
        }

        return BenchmarkRunResponse.builder()
                .id(r.getId())
                .approachName(r.getApproachName())
                .ragasFaithfulness(r.getRagasFaithfulness())
                .ragasAnswerRelevance(r.getRagasAnswerRelevance())
                .ragasContextPrecision(r.getRagasContextPrecision())
                .ragasContextRecall(r.getRagasContextRecall())
                .avgLatencyMs(r.getAvgLatencyMs())
                .runAt(r.getRunAt())
                .details(detailResponses)
                .build();
    }
}
