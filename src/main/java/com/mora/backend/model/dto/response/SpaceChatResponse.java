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
public class SpaceChatResponse {
    private boolean answerFound;
    private String answer;
    private List<SpaceCitation> citations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpaceCitation {
        private String quote;
        private Long documentId;
        private Integer pageNumber;
    }
}
