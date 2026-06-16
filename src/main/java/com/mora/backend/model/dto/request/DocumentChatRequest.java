package com.mora.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChatRequest {
    @NotNull(message = "Document ID không được để trống")
    private Long documentId;

    @NotBlank(message = "Câu hỏi không được để trống")
    private String question;

    private List<ChatMessageDto> history;
}
