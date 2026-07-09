package com.mora.backend.service.impl;

import com.mora.backend.client.AiServiceClient;
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
import org.springframework.transaction.annotation.Transactional;

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

    @Async
    @Transactional
    public void processDocumentAsync(Long documentId, byte[] content, String originalName, String contentType) {
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
                List<AiServiceClient.PythonPageResponse> parsedPages = aiServiceClient.parsePdf(content, originalName);
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
            try {
                aiServiceClient.indexDocument(doc.getId(), doc.getSpace().getId(), doc.getName(), pages);
            } catch (Exception e) {
                log.error("Failed to index document in AI Service, but continuing", e);
            }

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
