package com.mora.backend.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mora.backend.client.AiServiceClient;
import com.mora.backend.model.dto.response.ChatMessageResponse;
import com.mora.backend.model.dto.response.SpaceChatResponse;
import com.mora.backend.model.entity.ChatMessage;
import com.mora.backend.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class ChatMessageMapper {
    private ChatMessageMapper() {}
    public static ChatMessageResponse toResponse(ChatMessage msg, ObjectMapper objectMapper) {
        List<SpaceChatResponse.SpaceCitation> responseCitations = new ArrayList<>();
        if (msg.getCitations() != null && !msg.getCitations().isBlank()) {
            try {
                List<AiServiceClient.PythonChatResponse.Citation> citations = objectMapper.readValue(
                        msg.getCitations(),
                        new TypeReference<List<AiServiceClient.PythonChatResponse.Citation>>() {}
                );
                responseCitations = citations.stream()
                        .map(c -> SpaceChatResponse.SpaceCitation.builder()
                                .quote(c.quote)
                                .documentId(c.documentId)
                                .documentName(c.documentName)
                                .pageNumber(c.pageNumber)
                                .build())
                        .toList();
            } catch (Exception e) {
                log.error("Failed to deserialize citations for message ID: {}", msg.getId(), e);
            }
        }

        List<Long> selectedDocIds = new ArrayList<>();
        if (msg.getSelectedDocumentIds() != null && !msg.getSelectedDocumentIds().isBlank()) {
            try {
                selectedDocIds = objectMapper.readValue(msg.getSelectedDocumentIds(), new TypeReference<List<Long>>() {});
            } catch (Exception e) {
                log.warn("Failed to deserialize selectedDocumentIds for message ID: {}", msg.getId());
            }
        }

        User u = msg.getUser();
        String userName = u != null ? (u.getFullName() != null ? u.getFullName() : u.getUsername()) : null;
        String userAvatar = u != null ? u.getAvatarUrl() : null;
        Long userId = u != null ? u.getId() : null;

        return ChatMessageResponse.builder()
                .id(msg.getId())
                .sender(msg.getSender())
                .messageType(msg.getMessageType())
                .text(msg.getText())
                .userId(userId)
                .userName(userName)
                .userAvatar(userAvatar)
                .timestamp(msg.getCreatedAt())
                .condensedQuestion(msg.getCondensedQuestion())
                .promptSent(msg.getPromptSent())
                .citations(responseCitations)
                .selectedDocumentIds(selectedDocIds)
                .build();
    }

}
