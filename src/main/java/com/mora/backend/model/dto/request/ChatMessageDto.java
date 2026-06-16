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
public class ChatMessageDto {
    @NotBlank(message = "Sender không được để trống")
    private String sender; // "user" hoặc "assistant"

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    private String text;
}
