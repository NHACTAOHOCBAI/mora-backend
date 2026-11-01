package com.mora.backend.config;

import com.mora.backend.model.entity.Document;
import com.mora.backend.model.entity.DocumentStatus;
import com.mora.backend.repository.DocumentRepository;
import com.mora.backend.service.DocumentReindexService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenAiReindexRunnerTest {
    @Test
    void attemptsRemainingDocumentsAndFailsStartupIfAnyReindexFails() {
        var documents = mock(DocumentRepository.class);
        var service = mock(DocumentReindexService.class);
        when(documents.findAll()).thenReturn(List.of(
                Document.builder().id(1L).status(DocumentStatus.READY).build(),
                Document.builder().id(2L).status(DocumentStatus.READY).build()));
        doThrow(new IllegalStateException("unavailable")).when(service).reindex(1L);
        assertThrows(IllegalStateException.class, () -> new OpenAiReindexRunner(documents, service).run(null));
        verify(service).reindex(1L);
        verify(service).reindex(2L);
    }

    @Test
    void freshDatabaseCompletesWithoutIndexingAnything() {
        var documents = mock(DocumentRepository.class);
        var service = mock(DocumentReindexService.class);
        when(documents.findAll()).thenReturn(List.of());
        assertDoesNotThrow(() -> new OpenAiReindexRunner(documents, service).run(null));
        verifyNoInteractions(service);
    }
}
