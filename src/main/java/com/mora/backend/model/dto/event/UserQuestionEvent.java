package com.mora.backend.model.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserQuestionEvent {
    private Long spaceId;
    private String question;
    private String chatSummary;
    private Long userMessageId;
    private Long assistantMessageId;
    private List<ContextItem> context;
    private List<HistoryItem> history;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextItem {
        private int pageNumber;
        private String text;
        private String documentName;
        private Long documentId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryItem {
        private String sender;
        private String text;
    }
}
