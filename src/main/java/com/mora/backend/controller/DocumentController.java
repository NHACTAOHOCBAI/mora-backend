package com.mora.backend.controller;

import com.mora.backend.model.dto.response.ApiResponse;
import com.mora.backend.model.dto.response.DocumentResponse;
import com.mora.backend.model.entity.Document;
import com.mora.backend.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Document API", description = "Các API liên quan đến quản lý Tài liệu (PDF)")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    @Operation(summary = "Tải lên tài liệu PDF mới vào không gian học tập")
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            @RequestParam("spaceId") Long spaceId,
            @RequestParam("file") MultipartFile file) throws IOException {

        Document doc = documentService.uploadDocument(
                spaceId,
                file.getOriginalFilename(),
                file.getBytes(),
                file.getContentType()
        );

        DocumentResponse response = convertToResponse(doc);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<DocumentResponse>builder()
                        .message("Tải lên tài liệu thành công")
                        .result(response)
                        .build()
        );
    }

    @GetMapping("/space/{spaceId}")
    @Operation(summary = "Lấy danh sách tài liệu của một Không gian học tập")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getDocumentsBySpace(@PathVariable("spaceId") Long spaceId) {
        List<Document> docs = documentService.getDocumentsBySpace(spaceId);
        List<DocumentResponse> response = docs.stream().map(this::convertToResponse).toList();
        return ResponseEntity.ok(
                ApiResponse.<List<DocumentResponse>>builder()
                        .message("Lấy danh sách tài liệu thành công")
                        .result(response)
                        .build()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết thông tin một tài liệu")
    public ResponseEntity<ApiResponse<DocumentResponse>> getDocumentById(@PathVariable("id") Long id) {
        Document doc = documentService.getDocumentById(id);
        DocumentResponse response = convertToResponse(doc);
        return ResponseEntity.ok(
                ApiResponse.<DocumentResponse>builder()
                        .message("Lấy thông tin tài liệu thành công")
                        .result(response)
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa một tài liệu khỏi hệ thống")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable("id") Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("Xóa tài liệu thành công")
                        .build()
        );
    }

    private DocumentResponse convertToResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .name(doc.getName())
                .storageUrl(doc.getStorageUrl())
                .fileSize(doc.getFileSize())
                .contentType(doc.getContentType())
                .spaceId(doc.getSpace().getId())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
