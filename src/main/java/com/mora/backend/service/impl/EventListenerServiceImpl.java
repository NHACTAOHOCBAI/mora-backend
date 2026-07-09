package com.mora.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mora.backend.config.RabbitMQConfig;
import com.mora.backend.model.dto.event.AnswerVerifiedEvent;
import com.mora.backend.model.dto.response.SpaceChatResponse;
import com.mora.backend.model.entity.ChatMessage;
import com.mora.backend.model.entity.Space;
import com.mora.backend.repository.ChatMessageRepository;
import com.mora.backend.service.SseService;
import com.mora.backend.client.AiServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventListenerServiceImpl {

    private final ChatMessageRepository chatMessageRepository;
    private final SseService sseService;
    private final ChatSummaryHelper chatSummaryHelper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queues = RabbitMQConfig.ANSWER_VERIFIED_QUEUE)
    @Transactional
    public void handleAnswerVerified(AnswerVerifiedEvent event) {
        log.info("Received AnswerVerifiedEvent from RabbitMQ: spaceId={}, assistantMessageId={}", 
                event.getSpaceId(), event.getAssistantMessageId());

        try {
            // 1. Tìm tin nhắn assistant đang xử lý
            ChatMessage assistantMessage = chatMessageRepository.findById(event.getAssistantMessageId())
                    .orElseThrow(() -> new IllegalArgumentException("Assistant message not found: " + event.getAssistantMessageId()));

            // 2. Chuyển đổi citations sang JSON string để lưu vào DB
            String citationsJson = "";
            if (event.getCitations() != null) {
                try {
                    citationsJson = objectMapper.writeValueAsString(event.getCitations());
                } catch (Exception e) {
                    log.error("Failed to serialize citations", e);
                }
            }

            // 3. Cập nhật câu trả lời từ AI
            assistantMessage.setText(event.getAnswer());
            assistantMessage.setCondensedQuestion(event.getCondensedQuestion());
            assistantMessage.setPromptSent(event.getPromptSent());
            assistantMessage.setCitations(citationsJson);
            chatMessageRepository.save(assistantMessage);

            // 4. Định dạng Response DTO cho SSE
            List<SpaceChatResponse.SpaceCitation> responseCitations = new ArrayList<>();
            if (event.getCitations() != null) {
                responseCitations = event.getCitations().stream()
                        .map(c -> SpaceChatResponse.SpaceCitation.builder()
                                .quote(c.getQuote())
                                .documentId(c.getDocumentId())
                                .documentName(c.getDocumentName())
                                .pageNumber(c.getPageNumber())
                                .build())
                        .toList();
            }

            SpaceChatResponse chatResponse = SpaceChatResponse.builder()
                    .answerFound(true)
                    .answer(event.getAnswer())
                    .citations(responseCitations)
                    .condensedQuestion(event.getCondensedQuestion())
                    .promptSent(event.getPromptSent())
                    .build();

            // 5. Gửi dữ liệu qua SSE cho Frontend và kết thúc emitter
            sseService.sendEvent(event.getAssistantMessageId(), chatResponse);
            sseService.completeEmitter(event.getAssistantMessageId());

            // 6. Cập nhật tóm tắt lịch sử hội thoại chạy ngầm
            try {
                Space space = assistantMessage.getSpace();
                if (space != null) {
                    // Lấy tin nhắn user tương ứng
                    ChatMessage userMessage = chatMessageRepository.findById(event.getUserMessageId()).orElse(null);
                    if (userMessage != null) {
                        List<AiServiceClient.PythonChatRequest.HistoryItem> historyToUpdate = new ArrayList<>();
                        
                        // Lấy lịch sử cũ trước đó (nếu có)
                        // Trong giải pháp bất đồng bộ, chúng ta có thể build history hoặc chỉ gửi tin nhắn mới nhất
                        AiServiceClient.PythonChatRequest.HistoryItem newUserMsg = new AiServiceClient.PythonChatRequest.HistoryItem();
                        newUserMsg.sender = "user";
                        newUserMsg.text = userMessage.getText();
                        historyToUpdate.add(newUserMsg);

                        AiServiceClient.PythonChatRequest.HistoryItem newAssistantMsg = new AiServiceClient.PythonChatRequest.HistoryItem();
                        newAssistantMsg.sender = "assistant";
                        newAssistantMsg.text = event.getAnswer();
                        historyToUpdate.add(newAssistantMsg);

                        chatSummaryHelper.updateSpaceChatSummary(space.getId(), historyToUpdate);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to update space chat summary in listener", e);
            }

        } catch (Exception e) {
            log.error("Error processing AnswerVerifiedEvent", e);
            sseService.sendError(event.getAssistantMessageId(), e);
        }
    }
}
