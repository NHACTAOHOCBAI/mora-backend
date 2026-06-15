package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.response.DocumentDetailResponse;
import com.mora.backend.model.dto.response.DocumentPageResponse;
import com.mora.backend.model.dto.response.DocumentResponse;
import com.mora.backend.model.entity.Document;
import com.mora.backend.model.entity.DocumentPage;
import com.mora.backend.repository.DocumentPageRepository;
import com.mora.backend.repository.DocumentRepository;
import com.mora.backend.service.DocumentService;
import com.mora.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final StorageService storageService;
    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;

    @Override
    @Transactional
    public DocumentResponse uploadAndProcessDocument(MultipartFile file) {
        // Validation: Verify if file is PDF
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        if (!Objects.equals(contentType, "application/pdf") && 
            (filename == null || !filename.toLowerCase().endsWith(".pdf"))) {
            log.warn("Invalid file format uploaded: {}", filename);
            throw new AppException(ErrorCode.INVALID_FILE_FORMAT);
        }

        // 1. Upload to Supabase Storage
        String storageUrl;
        try {
            storageUrl = storageService.upload(file);
        } catch (Exception e) {
            log.error("Failed to upload file to storage service: {}", filename, e);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        // 2. Save Document metadata first
        Document document = Document.builder()
                .fileName(filename)
                .fileType("pdf")
                .storageUrl(storageUrl)
                .build();
        document = documentRepository.save(document);

        // 3. Process PDF pages text extraction
        log.info("Extracting text from PDF file '{}'...", filename);
        List<DocumentPage> pages = new ArrayList<>();
        try (PDDocument pdfDocument = Loader.loadPDF(file.getBytes())) {
            int pageCount = pdfDocument.getNumberOfPages();
            log.info("PDF file has {} pages", pageCount);
            
            PDFTextStripper textStripper = new PDFTextStripper();
            for (int i = 1; i <= pageCount; i++) {
                textStripper.setStartPage(i);
                textStripper.setEndPage(i);
                String pageContent = textStripper.getText(pdfDocument);
                
                DocumentPage page = DocumentPage.builder()
                        .document(document)
                        .pageNumber(i)
                        .content(pageContent != null ? pageContent.trim() : "")
                        .build();
                pages.add(page);
            }
        } catch (IOException e) {
            log.error("Error occurred while reading and parsing PDF: {}", filename, e);
            // Clean up uploaded storage file on error
            try {
                String key = storageUrl.substring(storageUrl.lastIndexOf("/") + 1);
                storageService.delete(key);
            } catch (Exception ex) {
                log.error("Failed to clean up uploaded file from storage after parser failure", ex);
            }
            throw new RuntimeException("Lỗi bóc tách nội dung PDF: " + e.getMessage(), e);
        }

        // 4. Save all extracted pages
        documentPageRepository.saveAll(pages);
        document.setPages(pages);
        log.info("Successfully processed and saved document with ID: {} and {} pages", document.getId(), pages.size());

        // 5. Convert and return Response DTO
        return convertToDocumentResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDetailResponse getDocumentById(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Document with ID {} not found", id);
                    return new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
                });

        List<DocumentPage> pages = documentPageRepository.findByDocumentIdOrderByPageNumberAsc(id);
        
        List<DocumentPageResponse> pageResponses = pages.stream()
                .map(page -> DocumentPageResponse.builder()
                        .id(page.getId())
                        .pageNumber(page.getPageNumber())
                        .content(page.getContent())
                        .build())
                .toList();

        return DocumentDetailResponse.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .storageUrl(document.getStorageUrl())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .pages(pageResponses)
                .build();
    }

    @Override
    @Transactional
    public void deleteDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Document with ID {} not found for deletion", id);
                    return new AppException(ErrorCode.DOCUMENT_NOT_FOUND);
                });

        // Extract file name from Supabase storage URL (the text after the last '/')
        String storageUrl = document.getStorageUrl();
        String fileName = storageUrl.substring(storageUrl.lastIndexOf("/") + 1);

        // 1. Delete file on Supabase Storage
        try {
            storageService.delete(fileName);
        } catch (Exception e) {
            log.error("Failed to delete file '{}' from Supabase storage during document deletion", fileName, e);
        }

        // 2. Delete document from database (cascade deletes all related pages)
        documentRepository.delete(document);
        log.info("Successfully deleted document with ID: {} and its pages", id);
    }

    private DocumentResponse convertToDocumentResponse(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .fileType(document.getFileType())
                .storageUrl(document.getStorageUrl())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
