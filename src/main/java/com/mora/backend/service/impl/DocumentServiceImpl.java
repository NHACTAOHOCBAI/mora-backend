package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.entity.Document;
import com.mora.backend.model.entity.DocumentStatus;
import com.mora.backend.model.entity.Space;
import com.mora.backend.repository.DocumentPageRepository;
import com.mora.backend.repository.DocumentRepository;
import com.mora.backend.repository.SpaceRepository;
import com.mora.backend.service.DocumentService;
import com.mora.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final SpaceRepository spaceRepository;
    private final StorageService storageService;
    private final DocumentAsyncProcessor documentAsyncProcessor;

    @Override
    @Transactional
    public Document uploadDocument(Long spaceId, String name, byte[] content, String contentType) {
        log.info("Creating upload placeholder document: name={}, size={}, contentType={}, spaceId={}", name, content.length, contentType, spaceId);

        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));

        // 1. Create and save document metadata with status UPLOADING
        Document doc = Document.builder()
                .name(name)
                .storageUrl("") // temp URL, updated in async process
                .fileSize((long) content.length)
                .contentType(contentType)
                .space(space)
                .status(DocumentStatus.UPLOADING)
                .build();
        doc = documentRepository.save(doc);

        // 2. Trigger asynchronous processing
        documentAsyncProcessor.processDocumentAsync(doc.getId(), content, name, contentType);

        return doc;
    }

    @Override
    @Transactional(readOnly = true)
    public Document getDocumentById(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> getDocumentsBySpace(Long spaceId) {
        return documentRepository.findBySpaceId(spaceId);
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        // 1. Delete from storage
        String storageUrl = doc.getStorageUrl();
        if (storageUrl != null && !storageUrl.isBlank()) {
            String fileKey = storageUrl.substring(storageUrl.lastIndexOf("/") + 1);
            storageService.deleteFile(fileKey);
        }

        // 2. Delete document pages and document from DB
        documentPageRepository.deleteByDocumentId(documentId);
        documentRepository.delete(doc);
        log.info("Deleted document and pages for ID: {}", documentId);
    }
}
