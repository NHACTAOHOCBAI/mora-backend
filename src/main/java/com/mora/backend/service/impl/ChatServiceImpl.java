package com.mora.backend.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mora.backend.client.AiServiceClient;
import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.request.GroupChatMessageRequest;
import com.mora.backend.model.dto.request.SpaceChatRequest;
import com.mora.backend.model.dto.response.ChatMessageResponse;
import com.mora.backend.model.dto.response.SpaceChatResponse;
import com.mora.backend.model.entity.*;
import com.mora.backend.repository.*;
import com.mora.backend.service.ChatService;
import com.mora.backend.service.UserAiSettingService;
import com.mora.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final AiServiceClient aiServiceClient;
    private final ChatSummaryHelper chatSummaryHelper;
    private final UserService userService;
    private final UserAiSettingService userAiSettingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int SUMMARY_BATCH_INTERVAL = 6;

    @Override
    @Transactional
    public ChatMessageResponse sendGroupMessage(GroupChatMessageRequest request) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findById(request.getSpaceId())
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));

        SpaceRole userRole = getUserRole(space, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && userRole == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        ChatMessage userMessage = ChatMessage.builder()
                .sender("user")
                .messageType(MessageType.USER_MESSAGE)
                .user(currentUser)
                .text(request.getText())
                .space(space)
                .build();
        userMessage = chatMessageRepository.save(userMessage);

        log.info("Saved group message from user {} in space {}", currentUser.getUsername(), space.getId());
        ChatMessageResponse response = mapToResponse(userMessage);

        try {
            messagingTemplate.convertAndSend("/topic/spaces/" + space.getId() + "/messages", response);
            log.info("[WebSocket] Broadcasted group message #{} to space #{}", userMessage.getId(), space.getId());
        } catch (Exception e) {
            log.warn("[WebSocket] Failed to broadcast group message: {}", e.getMessage());
        }

        return response;
    }

    @Override
    @Transactional
    public SpaceChatResponse chatWithSpace(SpaceChatRequest request) {
        User currentUser = userService.getCurrentUser();
        UserAiSetting userSetting = userAiSettingService.getSettingForUser(currentUser);
        if (userSetting.getGeminiApiKey() == null || userSetting.getGeminiApiKey().isBlank()) {
            log.warn("User {} chưa cấu hình Gemini API Key", currentUser.getUsername());
            throw new AppException(ErrorCode.GEMINI_API_KEY_REQUIRED);
        }

        Space space = spaceRepository.findById(request.getSpaceId())
                .orElseThrow(() -> {
                    log.warn("Space with ID {} not found for chat", request.getSpaceId());
                    return new AppException(ErrorCode.SPACE_NOT_FOUND);
                });

        SpaceRole userRole = getUserRole(space, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && userRole == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // Lấy danh sách tài liệu
        List<Document> documents;
        if (request.getDocumentIds() != null && !request.getDocumentIds().isEmpty()) {
            documents = documentRepository.findAllById(request.getDocumentIds());
        } else {
            documents = documentRepository.findBySpaceId(request.getSpaceId());
        }

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

        // Lịch sử hội thoại
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

        String docIdsJson = "";
        if (request.getDocumentIds() != null && !request.getDocumentIds().isEmpty()) {
            try {
                docIdsJson = objectMapper.writeValueAsString(request.getDocumentIds());
            } catch (Exception e) {
                log.warn("Failed to serialize documentIds", e);
            }
        }

        // Lưu câu hỏi của User (AI_QUERY)
        ChatMessage userMessage = ChatMessage.builder()
                .sender("user")
                .messageType(MessageType.AI_QUERY)
                .user(currentUser)
                .text(request.getQuestion())
                .selectedDocumentIds(docIdsJson)
                .space(space)
                .build();
        userMessage = chatMessageRepository.save(userMessage);

        try {
            ChatMessageResponse userQueryResponse = mapToResponse(userMessage);
            messagingTemplate.convertAndSend("/topic/spaces/" + space.getId() + "/messages", userQueryResponse);
            log.info("[WebSocket] Broadcasted user AI query #{} to space #{}", userMessage.getId(), space.getId());
        } catch (Exception e) {
            log.warn("[WebSocket] Failed to broadcast user AI query: {}", e.getMessage());
        }

        // Gọi Python AI Service
        AiServiceClient.PythonChatRequest pythonRequest = new AiServiceClient.PythonChatRequest();
        pythonRequest.question = request.getQuestion();
        pythonRequest.spaceId = space.getId();
        pythonRequest.documentIds = request.getDocumentIds();
        pythonRequest.context = contextItems;
        pythonRequest.history = historyItems;
        pythonRequest.chatSummary = space.getChatSummary();
        pythonRequest.apiKey = userSetting.getGeminiApiKey();
        pythonRequest.chatModel = userSetting.getChatModel();
        pythonRequest.routerModel = userSetting.getRouterModel();
        pythonRequest.evaluatorModel = userSetting.getEvaluatorModel();

        AiServiceClient.PythonChatResponse pythonResponse = aiServiceClient.callChat(pythonRequest);

        userAiSettingService.recordUsage(currentUser.getId(), userSetting.getChatModel());
        userAiSettingService.recordUsage(currentUser.getId(), userSetting.getRouterModel());

        // Lưu phản hồi của Assistant
        String citationsJson = "";
        try {
            citationsJson = objectMapper.writeValueAsString(pythonResponse.citations);
        } catch (Exception e) {
            log.error("Failed to serialize citations", e);
        }

        ChatMessage assistantMessage = ChatMessage.builder()
                .sender("assistant")
                .messageType(MessageType.AI_RESPONSE)
                .text(pythonResponse.answer)
                .space(space)
                .condensedQuestion(pythonResponse.condensedQuestion)
                .promptSent(pythonResponse.promptSent)
                .citations(citationsJson)
                .selectedDocumentIds(docIdsJson)
                .build();
        assistantMessage = chatMessageRepository.save(assistantMessage);

        try {
            ChatMessageResponse assistantResponse = mapToResponse(assistantMessage);
            messagingTemplate.convertAndSend("/topic/spaces/" + space.getId() + "/messages", assistantResponse);
            log.info("[WebSocket] Broadcasted assistant response #{} to space #{}", assistantMessage.getId(), space.getId());
        } catch (Exception e) {
            log.warn("[WebSocket] Failed to broadcast assistant response: {}", e.getMessage());
        }

        // Kích hoạt tóm tắt ngầm nếu đạt chu kỳ
        try {
            long assistantMsgCount = chatMessageRepository.countBySpaceIdAndSender(space.getId(), "assistant");
            if (assistantMsgCount > 0 && assistantMsgCount % SUMMARY_BATCH_INTERVAL == 0) {
                log.info("Đạt chu kỳ tóm tắt hội thoại (Lượt thứ {}). Kích hoạt tóm tắt ngầm cho Space ID: {}", assistantMsgCount, space.getId());
                List<AiServiceClient.PythonChatRequest.HistoryItem> fullHistoryForSummary = new ArrayList<>(historyItems);
                
                AiServiceClient.PythonChatRequest.HistoryItem newUserMsg = new AiServiceClient.PythonChatRequest.HistoryItem();
                newUserMsg.sender = "user";
                newUserMsg.text = request.getQuestion();
                fullHistoryForSummary.add(newUserMsg);

                AiServiceClient.PythonChatRequest.HistoryItem newAssistantMsg = new AiServiceClient.PythonChatRequest.HistoryItem();
                newAssistantMsg.sender = "assistant";
                newAssistantMsg.text = pythonResponse.answer;
                fullHistoryForSummary.add(newAssistantMsg);

                chatSummaryHelper.updateSpaceChatSummary(
                        space.getId(), 
                        currentUser.getId(),
                        userSetting.getGeminiApiKey(), 
                        userSetting.getSummarizerModel(), 
                        fullHistoryForSummary
                );
            }
        } catch (Exception e) {
            log.error("Failed to trigger background chat summarization", e);
        }

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
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));

        SpaceRole userRole = getUserRole(space, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && userRole == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        List<ChatMessage> messages = chatMessageRepository.findBySpaceIdOrderByCreatedAtAsc(spaceId);
        return messages.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional
    public void clearSpaceChatHistory(Long spaceId) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));

        SpaceRole userRole = getUserRole(space, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && userRole != SpaceRole.OWNER) {
            throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        chatMessageRepository.deleteBySpaceId(spaceId);
        space.setChatSummary(null);
        spaceRepository.save(space);
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

    private SpaceRole getUserRole(Space space, User user) {
        if (user == null || space == null) return null;
        if (user.getRole() == Role.ROLE_ADMIN) return SpaceRole.OWNER;
        if (space.getUser() != null && space.getUser().getId().equals(user.getId())) return SpaceRole.OWNER;

        Optional<SpaceMember> memberOpt = spaceMemberRepository.findBySpaceIdAndUserId(space.getId(), user.getId());
        return memberOpt.map(SpaceMember::getRole).orElse(null);
    }
}
