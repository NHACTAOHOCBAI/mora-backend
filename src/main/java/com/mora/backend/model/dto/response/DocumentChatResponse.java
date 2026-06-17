package com.mora.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChatResponse {
    private boolean answerFound;
    private String answer;
    private List<Citation> citations;
    private String condensedQuestion;
    private String promptSent;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        private String quote;
        private Long documentId;
        private String documentName;
        private Integer pageNumber;
    }
}
