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
public class BenchmarkRunResponse {
    private Long id;
    private String approachName;
    private Double ragasFaithfulness;
    private Double ragasAnswerRelevance;
    private Double ragasContextPrecision;
    private Double ragasContextRecall;
    private Long avgLatencyMs;
    private LocalDateTime runAt;
    private List<DetailResponse> details;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailResponse {
        private Long id;
        private String question;
        private String retrievedContexts;
        private String generatedAnswer;
        private String groundTruth;
        private Long latencyMs;
        private Double faithfulness;
        private Double answerRelevance;
        private Double contextPrecision;
        private Double contextRecall;
    }
}
