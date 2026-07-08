package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.entity.Document;
import com.mora.backend.model.entity.DocumentPage;
import com.mora.backend.model.entity.Space;
import com.mora.backend.repository.DocumentPageRepository;
import com.mora.backend.repository.DocumentRepository;
import com.mora.backend.repository.SpaceRepository;
import com.mora.backend.service.DocumentService;
import com.mora.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final SpaceRepository spaceRepository;
    private final StorageService storageService;

    @Override
    @Transactional
    public Document uploadDocument(Long spaceId, String name, byte[] content, String contentType) {
        log.info("Uploading document: name={}, size={}, contentType={}, spaceId={}", name, content.length, contentType, spaceId);

        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));

        // 1. Upload to Cloud Storage
        String fileKey = UUID.randomUUID().toString() + "_" + name;
        String storageUrl = storageService.uploadFile(fileKey, content, contentType);

        // 2. Save Document metadata
        Document doc = Document.builder()
                .name(name)
                .storageUrl(storageUrl)
                .fileSize((long) content.length)
                .contentType(contentType)
                .space(space)
                .build();
        doc = documentRepository.save(doc);

        // 3. Extract text from PDF page-by-page
        if ("application/pdf".equalsIgnoreCase(contentType)) {
            try (PDDocument pdfDoc = Loader.loadPDF(content)) {
                PDFTextStripper stripper = new PDFTextStripper();
                int pageCount = pdfDoc.getNumberOfPages();
                List<DocumentPage> pages = new ArrayList<>();
                for (int p = 1; p <= pageCount; p++) {
                    stripper.setStartPage(p);
                    stripper.setEndPage(p);
                    String pageText = stripper.getText(pdfDoc);

                    DocumentPage page = DocumentPage.builder()
                            .document(doc)
                            .pageNumber(p)
                            .text(pageText != null ? pageText.trim() : "")
                            .build();
                    pages.add(page);
                }
                documentPageRepository.saveAll(pages);
                log.info("Extracted and saved {} pages for document ID: {}", pageCount, doc.getId());
            } catch (Exception e) {
                log.error("Failed to parse PDF using PDFBox", e);
                throw new AppException(ErrorCode.PDF_PROCESSING_FAILED);
            }
        } else {
            // For non-PDF, store as single page
            DocumentPage page = DocumentPage.builder()
                    .document(doc)
                    .pageNumber(1)
                    .text(new String(content))
                    .build();
            documentPageRepository.save(page);
        }

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
        String fileKey = storageUrl.substring(storageUrl.lastIndexOf("/") + 1);
        storageService.deleteFile(fileKey);

        // 2. Delete document pages and document from DB
        documentPageRepository.deleteByDocumentId(documentId);
        documentRepository.delete(doc);
        log.info("Deleted document and pages for ID: {}", documentId);
    }
}
