package com.mora.backend.service.impl;

import com.mora.backend.service.SseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseServiceImpl implements SseService {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter createEmitter(Long assistantMessageId) {
        // Emitter timeout set to 5 minutes (300,000 ms) to allow for LLM processing
        SseEmitter emitter = new SseEmitter(300_000L);
        emitters.put(assistantMessageId, emitter);

        emitter.onCompletion(() -> {
            log.info("SSE emitter completed for message ID: {}", assistantMessageId);
            emitters.remove(assistantMessageId);
        });

        emitter.onTimeout(() -> {
            log.warn("SSE emitter timed out for message ID: {}", assistantMessageId);
            emitter.complete();
            emitters.remove(assistantMessageId);
        });

        emitter.onError((ex) -> {
            log.error("SSE emitter error for message ID: {}", assistantMessageId, ex);
            emitter.complete();
            emitters.remove(assistantMessageId);
        });

        // Send an initial heartbeat/connected event
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("Connected successfully. Waiting for AI assistant..."));
        } catch (Exception e) {
            log.error("Failed to send initial SSE event for message ID: {}", assistantMessageId, e);
            emitter.complete();
            emitters.remove(assistantMessageId);
        }

        return emitter;
    }

    @Override
    public void sendEvent(Long assistantMessageId, Object data) {
        SseEmitter emitter = emitters.get(assistantMessageId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("ANSWER")
                        .data(data));
                log.info("Sent ANSWER event via SSE to message ID: {}", assistantMessageId);
            } catch (Exception e) {
                log.error("Error sending SSE event to message ID: {}", assistantMessageId, e);
                emitter.completeWithError(e);
                emitters.remove(assistantMessageId);
            }
        } else {
            log.warn("No active SSE emitter found for message ID: {}", assistantMessageId);
        }
    }

    @Override
    public void sendError(Long assistantMessageId, Throwable throwable) {
        SseEmitter emitter = emitters.get(assistantMessageId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("ERROR")
                        .data(throwable.getMessage()));
            } catch (Exception e) {
                log.error("Error sending error SSE event to message ID: {}", assistantMessageId, e);
            } finally {
                emitter.completeWithError(throwable);
                emitters.remove(assistantMessageId);
            }
        }
    }

    @Override
    public void completeEmitter(Long assistantMessageId) {
        SseEmitter emitter = emitters.get(assistantMessageId);
        if (emitter != null) {
            emitter.complete();
            emitters.remove(assistantMessageId);
            log.info("Completed SSE connection for message ID: {}", assistantMessageId);
        }
    }
}
