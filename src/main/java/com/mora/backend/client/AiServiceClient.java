package com.mora.backend.client;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.response.DocumentChatResponse;
import com.mora.backend.model.dto.response.SpaceChatResponse;
import com.mora.backend.model.dto.request.ChatMessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;

@Component
@Slf4j
@RequiredArgsConstructor
public class AiServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai-service.url:http://localhost:8000}")
    private String aiServiceUrl;

    public DocumentChatResponse chatWithDocument(String context, String question, List<String> base64Images, List<ChatMessageDto> history) {
        String url = aiServiceUrl + "/api/chat/document";
        Map<String, Object> request = Map.of(
                "context", context,
                "question", question,
                "base64Images", base64Images != null ? base64Images : List.of(),
                "history", history != null ? history : List.of()
        );
        try {
            return restTemplate.postForObject(url, request, DocumentChatResponse.class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Failed to call Python AI service /api/chat/document. Error response: {}", e.getResponseBodyAsString(), e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        } catch (Exception e) {
            log.error("Failed to call Python AI service /api/chat/document", e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        }
    }

    public SpaceChatResponse chatWithSpace(String context, String question, List<String> base64Images, List<ChatMessageDto> history) {
        String url = aiServiceUrl + "/api/chat/space";
        Map<String, Object> request = Map.of(
                "context", context,
                "question", question,
                "base64Images", base64Images != null ? base64Images : List.of(),
                "history", history != null ? history : List.of()
        );
        try {
            return restTemplate.postForObject(url, request, SpaceChatResponse.class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Failed to call Python AI service /api/chat/space. Error response: {}", e.getResponseBodyAsString(), e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        } catch (Exception e) {
            log.error("Failed to call Python AI service /api/chat/space", e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        }
    }

    public static class StudyNotesResponse {
        public String summary;
        public List<Flashcard> flashcards;

        public static class Flashcard {
            public String question;
            public String answer;
        }
    }

    public StudyNotesResponse generateStudyNotes(String context) {
        String url = aiServiceUrl + "/api/study/notes";
        Map<String, Object> request = Map.of("context", context);
        try {
            return restTemplate.postForObject(url, request, StudyNotesResponse.class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Failed to call Python AI service /api/study/notes. Error response: {}", e.getResponseBodyAsString(), e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        } catch (Exception e) {
            log.error("Failed to call Python AI service /api/study/notes", e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        }
    }

    public static class PythonEvaluationResponse {
        public String approachName;
        public Double faithfulness;
        public Double answerRelevance;
        public Double contextPrecision;
        public Double contextRecall;
        public Long avgLatencyMs;
        public List<Detail> details;

        public static class Detail {
            public String question;
            @JsonProperty("retrieved_contexts")
            public String retrievedContexts;
            @JsonProperty("generated_answer")
            public String generatedAnswer;
            public String groundTruth;
            public Long latencyMs;
            public Double faithfulness;
            public Double answerRelevance;
            public Double contextPrecision;
            public Double contextRecall;
        }
    }

    public PythonEvaluationResponse evaluateBenchmark(String approachName, List<Map<String, String>> dataset) {
        String url = aiServiceUrl + "/api/benchmark/evaluate";
        Map<String, Object> request = Map.of(
                "approach_name", approachName,
                "dataset", dataset
        );
        try {
            PythonEvaluationResponse response = restTemplate.postForObject(url, request, PythonEvaluationResponse.class);
            if (response != null && response.details != null) {
                for (var d : response.details) {
                    log.info("Python evaluation detail: question='{}', generatedAnswer='{}', retrievedContexts='{}', groundTruth='{}'", 
                            d.question, d.generatedAnswer, d.retrievedContexts, d.groundTruth);
                }
            }
            return response;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Failed to call Python AI service /api/benchmark/evaluate. Error response: {}", e.getResponseBodyAsString(), e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        } catch (Exception e) {
            log.error("Failed to call Python AI service /api/benchmark/evaluate", e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        }
    }
}
