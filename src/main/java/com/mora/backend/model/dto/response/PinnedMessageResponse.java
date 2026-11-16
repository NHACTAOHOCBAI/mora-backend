package com.mora.backend.model.dto.response;

import java.time.LocalDateTime;

public record PinnedMessageResponse(
        ChatMessageResponse message,
        Long pinnedByUserId,
        String pinnedByName,
        LocalDateTime pinnedAt
) {}
