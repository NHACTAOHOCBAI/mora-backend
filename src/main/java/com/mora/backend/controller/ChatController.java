package com.mora.backend.controller;

import com.mora.backend.model.dto.request.DocumentChatRequest;
import com.mora.backend.model.dto.request.SpaceChatRequest;
import com.mora.backend.model.dto.response.DocumentChatResponse;
import com.mora.backend.model.dto.response.SpaceChatResponse;
import com.mora.backend.model.dto.response.ChatMessageResponse;
import com.mora.backend.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat API", description = "Các API liên quan đến Hỏi đáp/Hội thoại với tài liệu và không gian học tập")
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    @Operation(summary = "Hỏi đáp với tài liệu sử dụng mô hình Gemini và trích dẫn trang")
    public ResponseEntity<DocumentChatResponse> chatWithDocument(@Valid @RequestBody DocumentChatRequest request) {
        DocumentChatResponse response = chatService.chatWithDocument(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/space")
    @Operation(summary = "Hỏi đáp trên toàn bộ Không gian học tập (nhiều tài liệu) sử dụng mô hình Gemini")
    public ResponseEntity<SpaceChatResponse> chatWithSpace(@Valid @RequestBody SpaceChatRequest request) {
        SpaceChatResponse response = chatService.chatWithSpace(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/document/{documentId}")
    @Operation(summary = "Lấy lịch sử cuộc trò chuyện của một tài liệu")
    public ResponseEntity<List<ChatMessageResponse>> getDocumentChatHistory(@PathVariable("documentId") Long documentId) {
        List<ChatMessageResponse> history = chatService.getDocumentChatHistory(documentId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/space/{spaceId}")
    @Operation(summary = "Lấy lịch sử cuộc trò chuyện của một Không gian học tập")
    public ResponseEntity<List<ChatMessageResponse>> getSpaceChatHistory(@PathVariable("spaceId") Long spaceId) {
        List<ChatMessageResponse> history = chatService.getSpaceChatHistory(spaceId);
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/document/{documentId}")
    @Operation(summary = "Xóa lịch sử cuộc trò chuyện của một tài liệu")
    public ResponseEntity<Void> clearDocumentChatHistory(@PathVariable("documentId") Long documentId) {
        chatService.clearDocumentChatHistory(documentId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/space/{spaceId}")
    @Operation(summary = "Xóa lịch sử cuộc trò chuyện của một Không gian học tập")
    public ResponseEntity<Void> clearSpaceChatHistory(@PathVariable("spaceId") Long spaceId) {
        chatService.clearSpaceChatHistory(spaceId);
        return ResponseEntity.noContent().build();
    }
}
