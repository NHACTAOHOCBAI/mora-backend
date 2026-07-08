package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.request.SpaceChatRequest;
import com.mora.backend.model.dto.response.SpaceChatResponse;
import com.mora.backend.model.dto.response.ChatMessageResponse;
import com.mora.backend.model.entity.Space;
import com.mora.backend.model.entity.ChatMessage;
import com.mora.backend.repository.SpaceRepository;
import com.mora.backend.repository.ChatMessageRepository;
import com.mora.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final SpaceRepository spaceRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Override
    @Transactional
    public SpaceChatResponse chatWithSpace(SpaceChatRequest request) {
        // 1. Kiểm tra Space tồn tại
        Space space = spaceRepository.findById(request.getSpaceId())
                .orElseThrow(() -> {
                    log.warn("Space with ID {} not found for chat", request.getSpaceId());
                    return new AppException(ErrorCode.SPACE_NOT_FOUND);
                });

        // 2. Lưu tin nhắn User gửi vào DB
        ChatMessage userMessage = ChatMessage.builder()
                .sender("user")
                .text(request.getQuestion())
                .space(space)
                .build();
        chatMessageRepository.save(userMessage);

        // 3. Tạo phản hồi mô phỏng (Mock Response)
        String mockAnswer = "Đây là phản hồi tự động từ hệ thống (vỏ rỗng chatbot). Bạn đã hỏi: " + request.getQuestion();
        
        // 4. Lưu phản hồi của Assistant vào DB
        ChatMessage assistantMessage = ChatMessage.builder()
                .sender("assistant")
                .text(mockAnswer)
                .space(space)
                .build();
        chatMessageRepository.save(assistantMessage);

        return SpaceChatResponse.builder()
                .answerFound(true)
                .answer(mockAnswer)
                .citations(List.of())
                .condensedQuestion(request.getQuestion())
                .promptSent("[MOCK PROMPT] - System prompt and AI services have been removed.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getSpaceChatHistory(Long spaceId) {
        List<ChatMessage> messages = chatMessageRepository.findBySpaceIdOrderByCreatedAtAsc(spaceId);
        return messages.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public void clearSpaceChatHistory(Long spaceId) {
        chatMessageRepository.deleteBySpaceId(spaceId);
    }

    private ChatMessageResponse mapToResponse(ChatMessage msg) {
        return ChatMessageResponse.builder()
                .id(msg.getId())
                .sender(msg.getSender())
                .text(msg.getText())
                .timestamp(msg.getCreatedAt())
                .build();
    }
}
