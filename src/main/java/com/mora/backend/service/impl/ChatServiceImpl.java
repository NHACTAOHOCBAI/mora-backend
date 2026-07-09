package com.mora.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mora.backend.client.AiServiceClient;
import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.request.SpaceChatRequest;
import com.mora.backend.model.dto.response.SpaceChatResponse;
import com.mora.backend.model.dto.response.ChatMessageResponse;
import com.mora.backend.model.entity.Space;
import com.mora.backend.model.entity.ChatMessage;
import com.mora.backend.model.entity.Document;
import com.mora.backend.model.entity.DocumentPage;
import com.mora.backend.repository.SpaceRepository;
import com.mora.backend.repository.ChatMessageRepository;
import com.mora.backend.repository.DocumentRepository;
import com.mora.backend.repository.DocumentPageRepository;
import com.mora.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final SpaceRepository spaceRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final AiServiceClient aiServiceClient;
    private final ChatSummaryHelper chatSummaryHelper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public SpaceChatResponse chatWithSpace(SpaceChatRequest request) {
        // 1. Kiểm tra Space tồn tại
        Space space = spaceRepository.findById(request.getSpaceId())
                .orElseThrow(() -> {
                    log.warn("Space with ID {} not found for chat", request.getSpaceId());
                    return new AppException(ErrorCode.SPACE_NOT_FOUND);
                });

        // 2. Lấy danh sách tài liệu và nội dung text các trang
        List<Document> documents = documentRepository.findBySpaceId(request.getSpaceId());
        List<AiServiceClient.PythonChatRequest.ContextItem> contextItems = new ArrayList<>();
        
        for (Document doc : documents) {
            List<DocumentPage> pages = documentPageRepository.findByDocumentIdOrderByPageNumberAsc(doc.getId());
            for (DocumentPage page : pages) {
                AiServiceClient.PythonChatRequest.ContextItem item = new AiServiceClient.PythonChatRequest.ContextItem();
                item.pageNumber = page.getPageNumber();
                item.text = page.getText();
                item.documentId = doc.getId();
                item.documentName = doc.getName();
                contextItems.add(item);
            }
        }

        // 3. Lấy lịch sử hội thoại được gửi lên từ Request
        List<AiServiceClient.PythonChatRequest.HistoryItem> historyItems = new ArrayList<>();
        if (request.getHistory() != null) {
            historyItems = request.getHistory().stream()
                    .map(h -> {
                        AiServiceClient.PythonChatRequest.HistoryItem item = new AiServiceClient.PythonChatRequest.HistoryItem();
                        item.sender = h.getSender();
                        item.text = h.getText();
                        return item;
                    })
                    .toList();
        }

        // 4. Lưu tin nhắn của User vào DB trước
        ChatMessage userMessage = ChatMessage.builder()
                .sender("user")
                .text(request.getQuestion())
                .space(space)
                .build();
        chatMessageRepository.save(userMessage);

        // 5. Gọi Python AI Service
        AiServiceClient.PythonChatRequest pythonRequest = new AiServiceClient.PythonChatRequest();
        pythonRequest.question = request.getQuestion();
        pythonRequest.context = contextItems;
        pythonRequest.history = historyItems;
        pythonRequest.chatSummary = space.getChatSummary();

        AiServiceClient.PythonChatResponse pythonResponse = aiServiceClient.callChat(pythonRequest);

        // 6. Lưu phản hồi của Assistant kèm Citations JSON
        String citationsJson = "";
        try {
            citationsJson = objectMapper.writeValueAsString(pythonResponse.citations);
        } catch (Exception e) {
            log.error("Failed to serialize citations", e);
        }

        ChatMessage assistantMessage = ChatMessage.builder()
                .sender("assistant")
                .text(pythonResponse.answer)
                .space(space)
                .condensedQuestion(pythonResponse.condensedQuestion)
                .promptSent(pythonResponse.promptSent)
                .citations(citationsJson)
                .build();
        chatMessageRepository.save(assistantMessage);

        // 6.5. Kích hoạt tiến trình chạy ngầm để tóm tắt lịch sử hội thoại
        try {
            List<AiServiceClient.PythonChatRequest.HistoryItem> fullHistoryForSummary = new ArrayList<>(historyItems);
            
            AiServiceClient.PythonChatRequest.HistoryItem newUserMsg = new AiServiceClient.PythonChatRequest.HistoryItem();
            newUserMsg.sender = "user";
            newUserMsg.text = request.getQuestion();
            fullHistoryForSummary.add(newUserMsg);

            AiServiceClient.PythonChatRequest.HistoryItem newAssistantMsg = new AiServiceClient.PythonChatRequest.HistoryItem();
            newAssistantMsg.sender = "assistant";
            newAssistantMsg.text = pythonResponse.answer;
            fullHistoryForSummary.add(newAssistantMsg);

            chatSummaryHelper.updateSpaceChatSummary(space.getId(), fullHistoryForSummary);
        } catch (Exception e) {
            log.error("Failed to trigger background chat summarization", e);
        }

        // 7. Chuyển đổi Citations sang định dạng Response DTO
        List<SpaceChatResponse.SpaceCitation> responseCitations = new ArrayList<>();
        if (pythonResponse.citations != null) {
            responseCitations = pythonResponse.citations.stream()
                    .map(c -> SpaceChatResponse.SpaceCitation.builder()
                            .quote(c.quote)
                            .documentId(c.documentId)
                            .documentName(c.documentName)
                            .pageNumber(c.pageNumber)
                            .build())
                    .toList();
        }

        return SpaceChatResponse.builder()
                .answerFound(true)
                .answer(pythonResponse.answer)
                .citations(responseCitations)
                .condensedQuestion(pythonResponse.condensedQuestion)
                .promptSent(pythonResponse.promptSent)
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
        spaceRepository.findById(spaceId).ifPresent(space -> {
            space.setChatSummary(null);
            spaceRepository.save(space);
        });
    }

    private ChatMessageResponse mapToResponse(ChatMessage msg) {
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
                log.error("Failed to deserialize citations from database for message ID: {}", msg.getId(), e);
            }
        }

        return ChatMessageResponse.builder()
                .id(msg.getId())
                .sender(msg.getSender())
                .text(msg.getText())
                .timestamp(msg.getCreatedAt())
                .condensedQuestion(msg.getCondensedQuestion())
                .promptSent(msg.getPromptSent())
                .citations(responseCitations)
                .build();
    }
}
