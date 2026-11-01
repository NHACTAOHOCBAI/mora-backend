package com.mora.backend.service.impl;

import com.mora.backend.client.AiServiceClient;
import com.mora.backend.benchmark.BenchmarkAiClient;
import com.mora.backend.model.entity.Document;
import com.mora.backend.model.entity.DocumentPage;
import com.mora.backend.model.entity.DocumentStatus;
import com.mora.backend.repository.DocumentPageRepository;
import com.mora.backend.repository.DocumentRepository;
import com.mora.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAsyncProcessor {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final StorageService storageService;
    private final AiServiceClient aiServiceClient;
    private final BenchmarkAiClient benchmarkAiClient;

    @Async
    public void processDocumentAsync(
            Long documentId, 
            Long spaceId, 
            byte[] content, 
            String originalName, 
            String contentType
    ) {
        processDocument(documentId, spaceId, content, originalName, contentType, false);
    }

    @Async
    public void processBenchmarkDocumentAsync(Long documentId, Long spaceId, byte[] content, String originalName) {
        processDocument(documentId, spaceId, content, originalName, "application/pdf", true);
    }

    private void processDocument(Long documentId, Long spaceId, byte[] content, String originalName,
            String contentType, boolean benchmark) {
        log.info("Starting async processing for document ID: {}", documentId);
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            log.error("Document with ID {} not found for async processing", documentId);
            return;
        }

        try {
            // 1. Upload to Cloud Storage
            String fileKey = UUID.randomUUID().toString() + "_" + originalName;
            String storageUrl = storageService.uploadFile(fileKey, content, contentType);
            
            doc.setStorageUrl(storageUrl);
            doc.setStatus(DocumentStatus.PARSING);
            doc = documentRepository.save(doc);

            // 2. Extract text (Parsing via Python AI Service Parser)
            List<DocumentPage> pages = new ArrayList<>();
            if ("application/pdf".equalsIgnoreCase(contentType)) {
                List<AiServiceClient.PythonPageResponse> parsedPages;
                if (benchmark) {
                    var result = benchmarkAiClient.parsePdf(content, originalName);
                    if (!"success".equals(result.path("status").asText()) || !result.path("pages").isArray())
                        throw new IllegalStateException("Invalid benchmark parse response");
                    parsedPages = new ArrayList<>();
                    for (var page : result.path("pages")) {
                        var item = new AiServiceClient.PythonPageResponse();
                        item.pageNumber = page.path("pageNumber").asInt();
                        item.text = page.path("text").asText();
                        parsedPages.add(item);
                    }
                } else parsedPages = aiServiceClient.parsePdf(content, originalName);
                for (AiServiceClient.PythonPageResponse pPage : parsedPages) {
                    DocumentPage page = DocumentPage.builder()
                            .document(doc)
                            .pageNumber(pPage.pageNumber)
                            .text(pPage.text != null ? pPage.text.trim() : "")
                            .build();
                    pages.add(page);
                }
                documentPageRepository.saveAll(pages);
                log.info("Extracted and structured {} pages via Python Parser for document ID: {}", pages.size(), doc.getId());
            } else {
                DocumentPage page = DocumentPage.builder()
                        .document(doc)
                        .pageNumber(1)
                        .text(new String(content))
                        .build();
                documentPageRepository.save(page);
                pages.add(page);
            }

            // Update status to INDEXING
            doc.setStatus(DocumentStatus.INDEXING);
            doc = documentRepository.save(doc);

            // 3. Call Python AI Service to Index (indexing vector & keywords)
            if (benchmark) {
                var result = benchmarkAiClient.call("index", java.util.Map.of(
                    "documentId", doc.getId(), "spaceId", spaceId, "documentName", doc.getName(),
                    "pages", pages.stream().map(p -> java.util.Map.of("pageNumber", (Object) p.getPageNumber(),
                        "text", (Object) p.getText())).toList()));
                if (!"success".equals(result.path("status").asText()))
                    throw new IllegalStateException("Invalid benchmark index response");
            } else aiServiceClient.indexDocument(doc.getId(), spaceId, doc.getName(), pages);

            // Update status to READY
            doc.setStatus(DocumentStatus.READY);
            documentRepository.save(doc);
            log.info("Async processing completed successfully for document ID: {}", documentId);

        } catch (Exception e) {
            log.error("Error occurred during async processing of document ID: {}", documentId, e);
            doc.setStatus(DocumentStatus.FAILED);
            documentRepository.save(doc);
        }
    }
}
