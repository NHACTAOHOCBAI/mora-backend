package com.mora.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private Long id;
    private String sender;
    private String text;
    private LocalDateTime timestamp;
    private List<Citation> citations;
    private String condensedQuestion;

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
