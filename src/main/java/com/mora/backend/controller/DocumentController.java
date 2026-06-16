package com.mora.backend.controller;

import com.mora.backend.model.dto.response.DocumentDetailResponse;
import com.mora.backend.model.dto.response.DocumentResponse;
import com.mora.backend.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Document API", description = "Các API liên quan đến tải lên và quản lý tài liệu PDF")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải lên file PDF và tự động bóc tách nội dung từng trang")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("spaceId") Long spaceId) {
        log.info("Received request to upload document: {} for space ID: {}", file.getOriginalFilename(), spaceId);
        DocumentResponse response = documentService.uploadAndProcessDocument(file, spaceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin chi tiết tài liệu kèm nội dung văn bản các trang")
    public ResponseEntity<DocumentDetailResponse> getDocumentById(@PathVariable("id") Long id) {
        log.info("Received request to get details of document ID: {}", id);
        DocumentDetailResponse response = documentService.getDocumentById(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa tài liệu khỏi hệ thống lưu trữ Cloud và Cơ sở dữ liệu")
    public ResponseEntity<Void> deleteDocument(@PathVariable("id") Long id) {
        log.info("Received request to delete document ID: {}", id);
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/generate-study-notes")
    @Operation(summary = "Tạo tóm tắt tài liệu & câu hỏi ôn tập (Flashcards) tự động bằng AI")
    public ResponseEntity<DocumentDetailResponse> generateStudyNotes(@PathVariable("id") Long id) {
        log.info("Received request to generate study notes for document ID: {}", id);
        DocumentDetailResponse response = documentService.generateStudyNotes(id);
        return ResponseEntity.ok(response);
    }
}
