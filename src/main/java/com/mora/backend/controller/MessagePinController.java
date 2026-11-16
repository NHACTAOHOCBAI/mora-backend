package com.mora.backend.controller;

import com.mora.backend.model.dto.response.*;
import com.mora.backend.service.MessagePinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/chat/space/{spaceId}/pins")
@RequiredArgsConstructor
public class MessagePinController {
    private final MessagePinService service;

    @GetMapping
    public ApiResponse<List<PinnedMessageResponse>> list(@PathVariable Long spaceId) {
        return ApiResponse.<List<PinnedMessageResponse>>builder().result(service.list(spaceId)).build();
    }

    @PutMapping("/{messageId}")
    public ApiResponse<Void> pin(@PathVariable Long spaceId, @PathVariable Long messageId) {
        service.pin(spaceId, messageId);
        return ApiResponse.<Void>builder().message("Đã ghim tin nhắn").build();
    }

    @DeleteMapping("/{messageId}")
    public ApiResponse<Void> unpin(@PathVariable Long spaceId, @PathVariable Long messageId) {
        service.unpin(spaceId, messageId);
        return ApiResponse.<Void>builder().message("Đã bỏ ghim tin nhắn").build();
    }
}
