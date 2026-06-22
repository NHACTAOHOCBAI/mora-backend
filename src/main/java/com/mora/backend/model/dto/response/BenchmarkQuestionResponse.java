package com.mora.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkQuestionResponse {
    private Long id;
    private String question;
    private String groundTruth;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
