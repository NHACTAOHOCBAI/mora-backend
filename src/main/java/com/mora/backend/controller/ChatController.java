package com.mora.backend.controller;

import com.mora.backend.model.dto.request.DocumentChatRequest;
import com.mora.backend.model.dto.response.DocumentChatResponse;
import com.mora.backend.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chat API", description = "Các API liên quan đến Hỏi đáp/Hội thoại với tài liệu")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @Operation(summary = "Hỏi đáp với tài liệu sử dụng mô hình Gemini và trích dẫn trang")
    public ResponseEntity<DocumentChatResponse> chatWithDocument(@Valid @RequestBody DocumentChatRequest request) {
        log.info("Received chat request for document ID: {} and question: '{}'", request.getDocumentId(), request.getQuestion());
        DocumentChatResponse response = chatService.chatWithDocument(request);
        return ResponseEntity.ok(response);
    }
}
