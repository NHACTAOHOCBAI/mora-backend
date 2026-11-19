package com.mora.backend.controller;

import com.mora.backend.model.dto.request.GroupChatMessageRequest;
import com.mora.backend.model.dto.request.SpaceChatRequest;
import com.mora.backend.model.dto.response.ApiResponse;
import com.mora.backend.model.dto.response.ChatMessageResponse;
import com.mora.backend.model.dto.response.SpaceChatResponse;
import com.mora.backend.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "Chat API", description = "Các API liên quan đến Hỏi đáp/Hội thoại với không gian học tập và Chat nhóm")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/space/message")
    @Operation(summary = "Gửi tin nhắn trao đổi giữa các thành viên trong nhóm (Không gọi AI)")
    public ResponseEntity<ApiResponse<ChatMessageResponse>> sendGroupMessage(@Valid @RequestBody GroupChatMessageRequest request) {
        ChatMessageResponse response = chatService.sendGroupMessage(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<ChatMessageResponse>builder()
                        .message("Gửi tin nhắn nhóm thành công")
                        .result(response)
                        .build()
        );
    }

    @PostMapping("/space")
    @Operation(summary = "Hỏi đáp trên Không gian học tập sử dụng mô hình AI RAG (@AI Trigger hoặc phím tắt)")
    public ResponseEntity<ApiResponse<SpaceChatResponse>> chatWithSpace(@Valid @RequestBody SpaceChatRequest request) {
        SpaceChatResponse response = chatService.chatWithSpace(request);
        return ResponseEntity.ok(
                ApiResponse.<SpaceChatResponse>builder()
                        .message("Hỏi đáp trên không gian học tập thành công")
                        .result(response)
                        .build()
        );
    }

    @PostMapping(value = "/space/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Hỏi AI và nhận câu trả lời theo từng phần")
    public SseEmitter streamChatWithSpace(@Valid @RequestBody SpaceChatRequest request,
                                          @RequestHeader("X-Chat-Request-Id") String requestId) {
        return chatService.streamChatWithSpace(request, requestId);
    }

    @DeleteMapping("/space/stream/{requestId}")
    @Operation(summary = "Dừng câu trả lời AI đang được sinh")
    public ResponseEntity<ApiResponse<Void>> cancelStream(@RequestParam Long spaceId,
                                                           @PathVariable String requestId) {
        chatService.cancelStream(spaceId, requestId);
        return ResponseEntity.ok(ApiResponse.<Void>builder().message("Đã yêu cầu dừng tạo câu trả lời").build());
    }

    @GetMapping("/space/{spaceId}")
    @Operation(summary = "Lấy toàn bộ lịch sử cuộc trò chuyện của một Không gian học tập (gồm chat nhóm và AI)")
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
    @Operation(summary = "Xóa lịch sử cuộc trò chuyện của một Không gian học tập (Chỉ OWNER hoặc ADMIN)")
    public ResponseEntity<ApiResponse<Void>> clearSpaceChatHistory(@PathVariable("spaceId") Long spaceId) {
        chatService.clearSpaceChatHistory(spaceId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("Xóa lịch sử chat của không gian học tập thành công")
                        .build()
        );
    }
}
