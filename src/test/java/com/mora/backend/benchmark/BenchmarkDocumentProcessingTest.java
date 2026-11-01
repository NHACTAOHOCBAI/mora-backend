package com.mora.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mora.backend.client.AiServiceClient;
import com.mora.backend.model.entity.*;
import com.mora.backend.repository.*;
import com.mora.backend.service.*;
import com.mora.backend.service.impl.DocumentAsyncProcessor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BenchmarkDocumentProcessingTest {
    @Test void benchmarkIngestionSendsNoProviderCredentialsFromBackend() throws Exception {
        var docs=mock(DocumentRepository.class); var pages=mock(DocumentPageRepository.class);
        var storage=mock(StorageService.class); var regular=mock(AiServiceClient.class);
        var benchmark=mock(BenchmarkAiClient.class);
        var processor=new DocumentAsyncProcessor(docs,pages,storage,regular,benchmark);
        var doc=new Document();doc.setId(1L);doc.setName("source.pdf");
        when(docs.findById(1L)).thenReturn(Optional.of(doc));when(docs.save(any())).thenAnswer(c -> c.getArgument(0));
        when(storage.uploadFile(anyString(),any(),eq("application/pdf"))).thenReturn("https://example.test/source.pdf");
        when(benchmark.parsePdf(any(),eq("source.pdf"))).thenReturn(new ObjectMapper().readTree("{\"status\":\"success\",\"pages\":[{\"pageNumber\":1,\"text\":\"parsed\"}]}"));
        when(benchmark.call(eq("index"),any())).thenAnswer(call -> {
            Map<?,?> payload=call.getArgument(1);
            assertEquals(Set.of("documentId","spaceId","documentName","pages"),payload.keySet());
            assertFalse(payload.containsKey("apiKey"));return new ObjectMapper().createObjectNode().put("status","success");
        });
        processor.processBenchmarkDocumentAsync(1L,2L,new byte[]{1},"source.pdf");
        assertEquals(DocumentStatus.READY,doc.getStatus());
        verify(benchmark).parsePdf(any(),eq("source.pdf"));verify(benchmark).call(eq("index"),any());
        verifyNoInteractions(regular);verify(pages).saveAll(any());
    }
}
