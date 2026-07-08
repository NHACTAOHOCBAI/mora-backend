package com.mora.backend.controller;

import com.mora.backend.model.dto.request.SpaceChatRequest;
import com.mora.backend.model.dto.response.ApiResponse;
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
@Tag(name = "Chat API", description = "Các API liên quan đến Hỏi đáp/Hội thoại với không gian học tập")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/space")
    @Operation(summary = "Hỏi đáp trên toàn bộ Không gian học tập sử dụng mô hình AI")
    public ResponseEntity<ApiResponse<SpaceChatResponse>> chatWithSpace(@Valid @RequestBody SpaceChatRequest request) {
        SpaceChatResponse response = chatService.chatWithSpace(request);
        return ResponseEntity.ok(
                ApiResponse.<SpaceChatResponse>builder()
                        .message("Hỏi đáp trên không gian học tập thành công")
                        .result(response)
                        .build()
        );
    }

    @GetMapping("/space/{spaceId}")
    @Operation(summary = "Lấy lịch sử cuộc trò chuyện của một Không gian học tập")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getSpaceChatHistory(@PathVariable("spaceId") Long spaceId) {
        List<ChatMessageResponse> history = chatService.getSpaceChatHistory(spaceId);
        return ResponseEntity.ok(
                ApiResponse.<List<ChatMessageResponse>>builder()
                        .message("Lấy lịch sử chat của không gian học tập thành công")
                        .result(history)
                        .build()
        );
    }

    @DeleteMapping("/space/{spaceId}")
    @Operation(summary = "Xóa lịch sử cuộc trò chuyện của một Không gian học tập")
    public ResponseEntity<ApiResponse<Void>> clearSpaceChatHistory(@PathVariable("spaceId") Long spaceId) {
        chatService.clearSpaceChatHistory(spaceId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("Xóa lịch sử chat của không gian học tập thành công")
                        .build()
        );
    }
}
