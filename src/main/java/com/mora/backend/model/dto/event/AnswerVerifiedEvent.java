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
public class AnswerVerifiedEvent {
    private Long spaceId;
    private Long userMessageId;
    private Long assistantMessageId;
    private String answer;
    private List<Citation> citations;
    private String condensedQuestion;
    private String promptSent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        private int pageNumber;
        private String quote;
        private Long documentId;
        private String documentName;
    }
}
