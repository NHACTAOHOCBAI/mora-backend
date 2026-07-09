package com.mora.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsyncChatResponse {
    private Long userMessageId;
    private Long assistantMessageId;
    private String status; // e.g. "PENDING"
}
