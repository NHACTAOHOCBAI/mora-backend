package com.mora.backend.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseService {
    SseEmitter createEmitter(Long assistantMessageId);
    void sendEvent(Long assistantMessageId, Object data);
    void sendError(Long assistantMessageId, Throwable throwable);
    void completeEmitter(Long assistantMessageId);
}
