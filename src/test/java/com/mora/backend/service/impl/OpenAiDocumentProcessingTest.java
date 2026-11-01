package com.mora.backend.service.impl;

import com.mora.backend.client.AiServiceClient;
import com.mora.backend.model.entity.*;
import com.mora.backend.repository.*;
import com.mora.backend.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenAiDocumentProcessingTest {
    @Mock DocumentRepository documents;
    @Mock DocumentPageRepository pages;
    @Mock StorageService storage;
    @Mock AiServiceClient ai;
    Document document;

    @BeforeEach
    void setup() {
        document = Document.builder().id(7L).name("lesson.txt")
                .space(Space.builder().id(1L).build()).build();
        when(documents.findById(7L)).thenReturn(Optional.of(document));
    }

    private DocumentAsyncProcessor processor() {
        return new DocumentAsyncProcessor(documents, pages, storage, ai);
    }

    private void setupUpload() {
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.uploadFile(anyString(), any(), anyString())).thenReturn("https://storage/lesson.txt");
    }

    @Test
    void uploadWithoutPersonalAiSettingsBecomesReadyOnlyAfterIndexing() {
        setupUpload();
        doAnswer(invocation -> {
            assertEquals(DocumentStatus.INDEXING, document.getStatus());
            return null;
        }).when(ai).indexDocument(eq(7L), eq(1L), eq("lesson.txt"), anyList());
        processor().processDocumentAsync(7L, 1L, "lesson".getBytes(), "lesson.txt", "text/plain");
        assertEquals(DocumentStatus.READY, document.getStatus());
        verify(ai).indexDocument(eq(7L), eq(1L), eq("lesson.txt"), anyList());
    }

    @Test
    void failedEmbeddingNeverMarksDocumentReady() {
        setupUpload();
        doThrow(new IllegalStateException("index failure")).when(ai)
                .indexDocument(eq(7L), eq(1L), anyString(), anyList());
        processor().processDocumentAsync(7L, 1L, "lesson".getBytes(), "lesson.txt", "text/plain");
        assertEquals(DocumentStatus.FAILED, document.getStatus());
    }

    @Test
    void reindexReusesSavedPagesAndCanBeRepeated() {
        var savedPages = List.of(DocumentPage.builder().pageNumber(2).text("Saved text").build());
        when(pages.findByDocumentIdOrderByPageNumberAsc(7L)).thenReturn(savedPages);
        var service = new DocumentReindexServiceImpl(documents, pages, ai);
        service.reindex(7L);
        service.reindex(7L);
        verify(ai, times(2)).indexDocument(7L, 1L, "lesson.txt", savedPages);
        verifyNoInteractions(storage);
        verify(pages, never()).saveAll(any());
        assertEquals(DocumentStatus.READY, document.getStatus());
    }

    @Test
    void failedReindexDoesNotSetReady() {
        document.setStatus(DocumentStatus.FAILED);
        var savedPages = List.of(DocumentPage.builder().pageNumber(1).text("Saved").build());
        when(pages.findByDocumentIdOrderByPageNumberAsc(7L)).thenReturn(savedPages);
        doThrow(new IllegalStateException("quota")).when(ai).indexDocument(7L, 1L, "lesson.txt", savedPages);
        assertThrows(IllegalStateException.class, () -> new DocumentReindexServiceImpl(documents, pages, ai).reindex(7L));
        assertEquals(DocumentStatus.FAILED, document.getStatus());
        verify(documents, never()).save(any());
    }
}
