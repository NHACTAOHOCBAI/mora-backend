package com.mora.backend.client;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
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

    public static class PythonChatRequest {
        public String question;
        public List<ContextItem> context;
        public List<HistoryItem> history;

        public static class ContextItem {
            public int pageNumber;
            public String text;
            public String documentName;
            public Long documentId;
        }

        public static class HistoryItem {
            public String sender;
            public String text;
        }
    }

    public static class PythonChatResponse {
        public String answer;
        public List<Citation> citations;
        public String condensedQuestion;
        public String promptSent;

        public static class Citation {
            public int pageNumber;
            public String quote;
            public Long documentId;
            public String documentName;
        }
    }

    public PythonChatResponse callChat(PythonChatRequest request) {
        String url = aiServiceUrl + "/api/chat";
        try {
            return restTemplate.postForObject(url, request, PythonChatResponse.class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Failed to call Python AI service /api/chat. Error response: {}", e.getResponseBodyAsString(), e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        } catch (Exception e) {
            log.error("Failed to call Python AI service /api/chat", e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        }
    }
}
