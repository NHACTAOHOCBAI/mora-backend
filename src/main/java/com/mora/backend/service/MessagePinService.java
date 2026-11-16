package com.mora.backend.service;

import com.mora.backend.model.dto.response.PinnedMessageResponse;
import java.util.List;

public interface MessagePinService {
    List<PinnedMessageResponse> list(Long spaceId);
    void pin(Long spaceId, Long messageId);
    void unpin(Long spaceId, Long messageId);
}
