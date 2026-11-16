package com.mora.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChatStateNotifierTest {
    @Test
    void publishesOnlyAfterCommitAndNeverOnRollback() {
        var socket = mock(SimpMessagingTemplate.class);
        var notifier = new ChatStateNotifier(socket);
        TransactionSynchronizationManager.initSynchronization();
        try {
            notifier.changed(2L, true);
            verifyNoInteractions(socket);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
            verify(socket).convertAndSend(eq("/topic/spaces/2/pins"), (Object) argThat(
                    value -> value instanceof java.util.Map<?, ?> map && Boolean.TRUE.equals(map.get("historyCleared"))));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        reset(socket);
        TransactionSynchronizationManager.initSynchronization();
        try {
            notifier.changed(2L, false);
            TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                    sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(socket);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
