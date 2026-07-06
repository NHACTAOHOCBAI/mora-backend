package com.mora.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkQuestionRequest {

    @NotBlank(message = "Câu hỏi không được trống")
    private String question;

    @NotBlank(message = "Đáp án chuẩn không được trống")
    private String groundTruth;

    private Long documentId;
}
