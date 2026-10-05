package com.mora.backend.model.dto.response;

import com.mora.backend.model.entity.MessageType;
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
    private String sender; // "user" hoặc "assistant"
    private MessageType messageType; // USER_MESSAGE, AI_QUERY, AI_RESPONSE
    private String text;
    private Long userId;
    private String userName;
    private String userAvatar;
    private LocalDateTime timestamp;
    private String condensedQuestion;
    private String promptSent;
    private List<SpaceChatResponse.SpaceCitation> citations;
    private List<Long> selectedDocumentIds;
}
