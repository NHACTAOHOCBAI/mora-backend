package com.mora.backend.client;

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

@Component
@Slf4j
@RequiredArgsConstructor
public class AiServiceClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai-service.url:http://localhost:8000}")
    private String aiServiceUrl;

    public DocumentChatResponse chatWithDocument(String context, String question, String base64Image, List<ChatMessageDto> history) {
        String url = aiServiceUrl + "/api/chat/document";
        Map<String, Object> request = Map.of(
                "context", context,
                "question", question,
                "base64Image", base64Image != null ? base64Image : "",
                "history", history != null ? history : List.of()
        );
        try {
            return restTemplate.postForObject(url, request, DocumentChatResponse.class);
        } catch (Exception e) {
            log.error("Failed to call Python AI service /api/chat/document", e);
            throw new RuntimeException("Lỗi kết nối tới AI Engine: " + e.getMessage(), e);
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
        } catch (Exception e) {
            log.error("Failed to call Python AI service /api/chat/space", e);
            throw new RuntimeException("Lỗi kết nối tới AI Engine: " + e.getMessage(), e);
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
        } catch (Exception e) {
            log.error("Failed to call Python AI service /api/study/notes", e);
            throw new RuntimeException("Lỗi kết nối tới AI Engine: " + e.getMessage(), e);
        }
    }
}
