package com.mora.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Map;

@lombok.extern.slf4j.Slf4j
@Component
@RequiredArgsConstructor
public class ChatStateNotifier {
    private final SimpMessagingTemplate messagingTemplate;

    public void changed(Long spaceId, boolean historyCleared) {
        // Never announce a database change before it is committed.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    messagingTemplate.convertAndSend("/topic/spaces/" + spaceId + "/pins",
                            (Object) Map.of("historyCleared", historyCleared));
                } catch (org.springframework.messaging.MessagingException error) {
                    log.warn("Pin change committed but realtime delivery failed for space {}", spaceId, error);
                }
            }
        });
    }
}
