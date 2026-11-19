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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mora.backend.service.AiUsageService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Component
@Slf4j
@RequiredArgsConstructor
public class AiServiceClient {

    private final AiUsageService usage;

    private final RestTemplate restTemplate = createRestTemplate();

    private static RestTemplate createRestTemplate() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(600000);
        return new RestTemplate(factory);
    }

    @Value("${ai-service.url:http://localhost:8000}")
    private String aiServiceUrl;

    public static class PythonChatRequest {
        public String question;
        @JsonProperty("space_id")
        public Long spaceId;
        @JsonProperty("document_id")
        public Long documentId;
        @JsonProperty("document_ids")
        public List<Long> documentIds;
        public List<ContextItem> context;
        public List<HistoryItem> history;
        @JsonProperty("chat_summary")
        public String chatSummary;

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
        public List<AiUsageService.UsageItem> usage;

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
            PythonChatResponse response = restTemplate.postForObject(url, request, PythonChatResponse.class);
            if (response != null) usage.record(response.usage, request.spaceId, request.documentId);
            return response;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Failed to call Python AI service /api/chat. Error response: {}", e.getResponseBodyAsString(), e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        } catch (Exception e) {
            log.error("Failed to call Python AI service /api/chat", e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        }
    }

    public record StreamEvent(String name, JsonNode payload) {}

    /** Proxy Python SSE without buffering the generated answer. */
    public void streamChat(PythonChatRequest payload, Consumer<StreamEvent> consumer, BooleanSupplier cancelled) {
        String url = aiServiceUrl + "/api/chat/stream";
        ObjectMapper mapper = new ObjectMapper();
        try {
            restTemplate.execute(url, org.springframework.http.HttpMethod.POST,
                    request -> {
                        request.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                        mapper.writeValue(request.getBody(), payload);
                    }, response -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line, event = "message", data = null;
                            while (!cancelled.getAsBoolean() && (line = reader.readLine()) != null) {
                                if (line.startsWith("event:")) event = line.substring(6).trim();
                                else if (line.startsWith("data:")) data = line.substring(5).trim();
                                else if (line.isBlank() && data != null) {
                                    consumer.accept(new StreamEvent(event, mapper.readTree(data)));
                                    event = "message"; data = null;
                                }
                            }
                        }
                        return null;
                    });
        } catch (Exception e) {
            if (!cancelled.getAsBoolean()) {
                log.error("Failed to stream Python AI service", e);
                throw new AppException(ErrorCode.AI_ENGINE_ERROR);
            }
        }
    }

    public static class PythonSummarizeRequest {
        public List<PythonChatRequest.HistoryItem> history;
        @JsonProperty("previous_summary")
        public String previousSummary;

    }

    public static class PythonSummarizeResponse {
        public String summary;
        public List<AiUsageService.UsageItem> usage;
    }

    public PythonSummarizeResponse callSummarize(PythonSummarizeRequest request) {
        String url = aiServiceUrl + "/api/chat/summarize";
        try {
            PythonSummarizeResponse response = restTemplate.postForObject(url, request, PythonSummarizeResponse.class);
            if (response != null) usage.record(response.usage, null, null);
            return response;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Failed to call Python AI service /api/chat/summarize. Error response: {}", e.getResponseBodyAsString(), e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        } catch (Exception e) {
            log.error("Failed to call Python AI service /api/chat/summarize", e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        }
    }

    public static class PythonPageResponse {
        public int pageNumber;
        public String text;
    }

    public static class PythonParseResponse {
        public String status;
        public List<PythonPageResponse> pages;
        public List<AiUsageService.UsageItem> usage;
    }

    public List<PythonPageResponse> parsePdf(byte[] pdfBytes, String fileName) {
        String url = aiServiceUrl + "/api/parse";

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);

        org.springframework.util.LinkedMultiValueMap<String, Object> body = new org.springframework.util.LinkedMultiValueMap<>();
        org.springframework.core.io.ByteArrayResource fileResource = new org.springframework.core.io.ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        body.add("file", fileResource);

        org.springframework.http.HttpEntity<org.springframework.util.LinkedMultiValueMap<String, Object>> requestEntity =
                new org.springframework.http.HttpEntity<>(body, headers);

        try {
            PythonParseResponse response = restTemplate.postForObject(url, requestEntity, PythonParseResponse.class);
            if (response != null && "success".equalsIgnoreCase(response.status)) {
                usage.record(response.usage, null, null);
                return response.pages;
            }
            throw new AppException(ErrorCode.PDF_PROCESSING_FAILED);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse PDF through Python AI service", e);
            throw new AppException(ErrorCode.PDF_PROCESSING_FAILED);
        }
    }

    public void indexDocument(Long documentId, Long spaceId, String documentName, List<com.mora.backend.model.entity.DocumentPage> pages) {
        String url = aiServiceUrl + "/api/index";
        
        List<Map<String, Object>> pagesPayload = pages.stream()
                .map(p -> Map.of(
                        "pageNumber", (Object) p.getPageNumber(),
                        "text", (Object) (p.getText() == null ? "" : p.getText())
                ))
                .toList();

        Map<String, Object> request = new java.util.HashMap<>();
        request.put("documentId", documentId);
        request.put("spaceId", spaceId);
        request.put("documentName", documentName);
        request.put("pages", pagesPayload);

        try {
            PythonIndexResponse response = restTemplate.postForObject(url, request, PythonIndexResponse.class);
            if (response != null) usage.record(response.usage, spaceId, documentId);
            log.info("Successfully requested Python AI service to index document ID: {}", documentId);
        } catch (Exception e) {
            log.error("Failed to request Python AI service to index document ID: {}", documentId, e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        }
    }

    public static class PythonStudyGenerateRequest {
        public Long spaceId;
        public List<Long> documentIds;
        public Integer cardCount;
        public List<StudyContextItem> context;
        public static class StudyContextItem { public Long documentId; public String documentName; public Integer pageNumber; public String text; }
    }
    public static class PythonStudyGenerateResponse {
        public String title;
        public List<StudyFlashcard> flashcards;
        public List<StudyQuizQuestion> quizQuestions;
        public List<AiUsageService.UsageItem> usage;
        public static class StudyCitation { public Long documentId; public String documentName; public Integer pageNumber; public String quote; }
        public static class StudyFlashcard { public String front; public String back; public StudyCitation citation; }
        public static class StudyQuizQuestion { public String question; public List<String> options; public Integer correctOptionIndex; public String explanation; public StudyCitation citation; }
    }

    public PythonStudyGenerateResponse generateStudySet(PythonStudyGenerateRequest request) {
        try {
            PythonStudyGenerateResponse response = restTemplate.postForObject(aiServiceUrl + "/api/study/generate", request, PythonStudyGenerateResponse.class);
            if (response != null) usage.record(response.usage, request.spaceId, null);
            return response;
        } catch (Exception e) {
            log.error("Failed to generate study set through Python AI service", e);
            throw new AppException(ErrorCode.AI_ENGINE_ERROR);
        }
    }

    public static class PythonIndexResponse { public String status; public List<AiUsageService.UsageItem> usage; }
}
