package com.mora.backend.service.impl;

import com.mora.backend.client.AiServiceClient;
import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.entity.DocumentStatus;
import com.mora.backend.repository.DocumentPageRepository;
import com.mora.backend.repository.DocumentRepository;
import com.mora.backend.service.DocumentReindexService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentReindexServiceImpl implements DocumentReindexService {
    private final DocumentRepository documents;
    private final DocumentPageRepository pages;
    private final AiServiceClient aiService;

    @Override
    @Transactional
    public void reindex(Long documentId) {
        var document = documents.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
        var savedPages = pages.findByDocumentIdOrderByPageNumberAsc(documentId);
        if (savedPages.isEmpty()) {
            throw new AppException(ErrorCode.PDF_PROCESSING_FAILED);
        }
        aiService.indexDocument(documentId, document.getSpace().getId(), document.getName(), savedPages);
        document.setStatus(DocumentStatus.READY);
        documents.save(document);
    }
}
