package com.mora.backend.service.impl;

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
import com.mora.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    private final com.mora.backend.service.BillingService billingService;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.mora.backend.service.ChatStateNotifier chatStateNotifier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int SUMMARY_BATCH_INTERVAL = 6;
    private final Map<String, AtomicBoolean> activeStreams = new ConcurrentHashMap<>();

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
        touchActivity(space);

        log.info("Saved group message from user {} in space {}", currentUser.getEmail(), space.getId());
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

        Space space = spaceRepository.findById(request.getSpaceId())
                .orElseThrow(() -> {
                    log.warn("Space with ID {} not found for chat", request.getSpaceId());
                    return new AppException(ErrorCode.SPACE_NOT_FOUND);
                });

        SpaceRole userRole = getUserRole(space, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && userRole == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (billingService != null) billingService.requireCanUseAi(space);

        // Lấy danh sách tài liệu
        List<Document> documents;
        if (request.getDocumentIds() != null && !request.getDocumentIds().isEmpty()) {
            documents = documentRepository.findAllById(request.getDocumentIds());
            if (documents.size() != request.getDocumentIds().stream().distinct().count()
                    || documents.stream().anyMatch(doc -> !doc.getSpace().getId().equals(space.getId()))) {
                throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
            }
        } else {
            documents = documentRepository.findBySpaceId(request.getSpaceId());
        }
        documents = documents.stream().filter(doc -> doc.getStatus() == DocumentStatus.READY).toList();

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
        touchActivity(space);

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
        // Always constrain retrieval to existing, ready documents in this space.
        pythonRequest.documentIds = documents.stream().map(Document::getId).toList();
        pythonRequest.context = contextItems;
        pythonRequest.history = historyItems;
        pythonRequest.chatSummary = space.getChatSummary();

        AiServiceClient.PythonChatResponse pythonResponse = aiServiceClient.callChat(pythonRequest);


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
                .build();
    }

    @Override
    @Transactional
    public SseEmitter streamChatWithSpace(SpaceChatRequest request, String requestId) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findById(request.getSpaceId())
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));
        if (currentUser.getRole() != Role.ROLE_ADMIN && getUserRole(space, currentUser) == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (billingService != null) billingService.requireCanUseAi(space);
        String key = streamKey(space.getId(), requestId);
        if (activeStreams.putIfAbsent(key, new AtomicBoolean(false)) != null) {
            throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);
        }

        List<Document> documents = request.getDocumentIds() != null && !request.getDocumentIds().isEmpty()
                ? documentRepository.findAllById(request.getDocumentIds()) : documentRepository.findBySpaceId(space.getId());
        if (request.getDocumentIds() != null && !request.getDocumentIds().isEmpty() &&
                (documents.size() != request.getDocumentIds().stream().distinct().count() ||
                 documents.stream().anyMatch(doc -> !doc.getSpace().getId().equals(space.getId())))) {
            activeStreams.remove(key);
            throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        }
        documents = documents.stream().filter(doc -> doc.getStatus() == DocumentStatus.READY).toList();
        List<AiServiceClient.PythonChatRequest.ContextItem> context = new ArrayList<>();
        for (Document doc : documents) for (DocumentPage page : documentPageRepository.findByDocumentIdOrderByPageNumberAsc(doc.getId())) {
            AiServiceClient.PythonChatRequest.ContextItem item = new AiServiceClient.PythonChatRequest.ContextItem();
            item.pageNumber = page.getPageNumber(); item.text = page.getText(); item.documentId = doc.getId(); item.documentName = doc.getName();
            context.add(item);
        }
        List<AiServiceClient.PythonChatRequest.HistoryItem> history = request.getHistory() == null ? new ArrayList<>() : request.getHistory().stream().map(h -> {
            AiServiceClient.PythonChatRequest.HistoryItem item = new AiServiceClient.PythonChatRequest.HistoryItem();
            item.sender = h.getSender(); item.text = h.getText(); return item;
        }).toList();
        String docIdsJson = "";
        try { if (request.getDocumentIds() != null) docIdsJson = objectMapper.writeValueAsString(request.getDocumentIds()); } catch (Exception ignored) { }
        ChatMessage userMessage = chatMessageRepository.save(ChatMessage.builder().sender("user").messageType(MessageType.AI_QUERY)
                .user(currentUser).text(request.getQuestion()).selectedDocumentIds(docIdsJson).space(space).build());
        touchActivity(space);
        messagingTemplate.convertAndSend("/topic/spaces/" + space.getId() + "/messages", mapToResponse(userMessage));

        AiServiceClient.PythonChatRequest python = new AiServiceClient.PythonChatRequest();
        python.question = request.getQuestion(); python.spaceId = space.getId(); python.documentIds = documents.stream().map(Document::getId).toList(); python.streamId = requestId;
        python.context = context; python.history = history; python.chatSummary = space.getChatSummary();
        SseEmitter emitter = new SseEmitter(0L);
        AtomicBoolean cancelled = activeStreams.get(key);
        AtomicLong sequence = new AtomicLong();
        emitter.onTimeout(() -> {
            cancelled.set(true);
            CompletableFuture.runAsync(() -> aiServiceClient.cancelStream(requestId));
            activeStreams.remove(key);
        });
        emitter.onError(error -> {
            cancelled.set(true);
            CompletableFuture.runAsync(() -> aiServiceClient.cancelStream(requestId));
            activeStreams.remove(key);
        });
        emitter.onCompletion(() -> activeStreams.remove(key));
        final String selectedDocumentIds = docIdsJson;
        emitStreamEvent(space, emitter, requestId, sequence, "accepted", Map.of("message", mapToResponse(userMessage)));
        CompletableFuture.runAsync(() -> runStream(space, python, emitter, key, cancelled, selectedDocumentIds, requestId, sequence));
        return emitter;
    }

    @Override
    public void cancelStream(Long spaceId, String requestId) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findById(spaceId).orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));
        if (currentUser.getRole() != Role.ROLE_ADMIN && getUserRole(space, currentUser) == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        AtomicBoolean cancelled = activeStreams.get(streamKey(spaceId, requestId));
        if (cancelled != null) cancelled.set(true);
        CompletableFuture.runAsync(() -> aiServiceClient.cancelStream(requestId));
    }

    private void runStream(Space space, AiServiceClient.PythonChatRequest python, SseEmitter emitter, String key,
                           AtomicBoolean cancelled, String documentIdsJson, String generationId, AtomicLong sequence) {
        StringBuilder answer = new StringBuilder();
        List<SpaceChatResponse.SpaceCitation> citations = new ArrayList<>();
        AtomicReference<String> upstreamError = new AtomicReference<>();
        try {
            aiServiceClient.streamChat(python, event -> {
                if ("error".equals(event.name())) {
                    upstreamError.set(event.payload().path("message").asText(ErrorCode.AI_ENGINE_ERROR.getMessage()));
                    return;
                }
                try {
                    if ("delta".equals(event.name())) {
                        String text = event.payload().path("text").asText(""); answer.append(text);
                    } else if ("citations".equals(event.name()) && event.payload().has("citations")) {
                        for (var node : event.payload().path("citations")) citations.add(SpaceChatResponse.SpaceCitation.builder()
                                .pageNumber(node.path("pageNumber").asInt()).quote(node.path("quote").asText())
                                .documentId(node.hasNonNull("documentId") ? node.get("documentId").asLong() : null)
                                .documentName(node.path("documentName").asText(null)).build());
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = objectMapper.convertValue(event.payload(), Map.class);
                    emitStreamEvent(space, emitter, generationId, sequence, event.name(), payload);
                } catch (RuntimeException streamClosed) { cancelled.set(true); }
            }, cancelled::get);
            if (upstreamError.get() != null || (!cancelled.get() && answer.toString().isBlank())) {
                String message = Optional.ofNullable(upstreamError.get()).orElse(ErrorCode.AI_ENGINE_ERROR.getMessage());
                emitStreamEvent(space, emitter, generationId, sequence, "error", Map.of("text", message));
                emitter.complete();
                return;
            }
            String citationsJson = objectMapper.writeValueAsString(citations);
            ChatMessage message = chatMessageRepository.save(ChatMessage.builder().sender("assistant").messageType(MessageType.AI_RESPONSE)
                    .text(answer.toString()).space(space).citations(citationsJson).selectedDocumentIds(documentIdsJson).build());
            ChatMessageResponse response = mapToResponse(message);
            messagingTemplate.convertAndSend("/topic/spaces/" + space.getId() + "/messages", response);
            emitStreamEvent(space, emitter, generationId, sequence, "done",
                    Map.of("status", cancelled.get() ? "cancelled" : "completed", "message", response));
            emitter.complete();
        } catch (Exception error) {
            log.error("AI streaming failed", error);
            try { emitStreamEvent(space, emitter, generationId, sequence, "error", Map.of("text", "Dịch vụ AI đang gặp sự cố. Vui lòng thử lại sau.")); } catch (Exception ignored) { }
            emitter.completeWithError(error);
        } finally { activeStreams.remove(key); }
    }

    private String streamKey(Long spaceId, String requestId) { return spaceId + ":" + requestId; }

    private void emitStreamEvent(Space space, SseEmitter emitter, String generationId, AtomicLong sequence,
                                 String type, Map<String, ?> body) {
        Map<String, Object> event = new HashMap<>(body);
        event.put("type", type);
        event.put("generationId", generationId);
        event.put("sequence", sequence.incrementAndGet());
        try {
            emitter.send(SseEmitter.event().name(type).data(event));
        } catch (IOException error) {
            throw new RuntimeException(error);
        }
        messagingTemplate.convertAndSend("/topic/spaces/" + space.getId() + "/ai-stream", (Object) event);
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
        return messages.stream()
                .filter(message -> message.getMessageType() != MessageType.AI_RESPONSE || !message.getText().isBlank())
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public void clearSpaceChatHistory(Long spaceId) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findForUpdate(spaceId)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));

        SpaceRole userRole = getUserRole(space, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && userRole != SpaceRole.OWNER) {
            throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        chatMessageRepository.deleteBySpaceId(spaceId);
        space.setChatSummary(null);
        spaceRepository.save(space);
        chatStateNotifier.changed(spaceId, true);
    }

    private ChatMessageResponse mapToResponse(ChatMessage msg) {
        return com.mora.backend.util.ChatMessageMapper.toResponse(msg, objectMapper);
    }

    private void touchActivity(Space space) {
        space.setLastActivityAt(LocalDateTime.now());
        spaceRepository.save(space);
    }

    private SpaceRole getUserRole(Space space, User user) {
        if (user == null || space == null) return null;
        if (user.getRole() == Role.ROLE_ADMIN) return SpaceRole.OWNER;
        if (space.getUser() != null && space.getUser().getId().equals(user.getId())) return SpaceRole.OWNER;

        Optional<SpaceMember> memberOpt = spaceMemberRepository.findBySpaceIdAndUserId(space.getId(), user.getId());
        return memberOpt.map(SpaceMember::getRole).orElse(null);
    }
}
