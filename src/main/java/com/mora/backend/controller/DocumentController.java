package com.mora.backend.controller;

import com.mora.backend.model.dto.request.DocumentRenameRequest;
import com.mora.backend.model.dto.response.DocumentDetailResponse;
import com.mora.backend.model.dto.response.DocumentResponse;
import com.mora.backend.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Document API", description = "Các API liên quan đến tải lên và quản lý tài liệu PDF")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải lên file PDF và tự động bóc tách nội dung từng trang")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("spaceId") Long spaceId,
            @RequestParam(value = "vectorPathThreshold", required = false) Integer vectorPathThreshold) {
        DocumentResponse response = documentService.uploadAndProcessDocument(file, spaceId, vectorPathThreshold);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin chi tiết tài liệu kèm nội dung văn bản các trang")
    public ResponseEntity<DocumentDetailResponse> getDocumentById(@PathVariable("id") Long id) {
        DocumentDetailResponse response = documentService.getDocumentById(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa tài liệu khỏi hệ thống lưu trữ Cloud và Cơ sở dữ liệu")
    public ResponseEntity<Void> deleteDocument(@PathVariable("id") Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/generate-study-notes")
    @Operation(summary = "Tạo tóm tắt tài liệu & câu hỏi ôn tập (Flashcards) tự động bằng AI")
    public ResponseEntity<DocumentDetailResponse> generateStudyNotes(@PathVariable("id") Long id) {
        DocumentDetailResponse response = documentService.generateStudyNotes(id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/rename")
    @Operation(summary = "Đổi tên tài liệu")
    public ResponseEntity<DocumentResponse> renameDocument(
            @PathVariable("id") Long id,
            @Valid @RequestBody DocumentRenameRequest request) {
        DocumentResponse response = documentService.renameDocument(id, request.getFileName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/debug-images")
    @Operation(summary = "Debug xem thông tin các đối tượng hình ảnh trên từng trang")
    public ResponseEntity<java.util.List<com.mora.backend.model.dto.response.DocumentImageDebugResponse>> debugDocumentImages(@PathVariable("id") Long id) {
        java.util.List<com.mora.backend.model.dto.response.DocumentImageDebugResponse> response = documentService.debugDocumentImages(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/pages/{pageNumber}/images/{imageName}")
    @Operation(summary = "Trích xuất và trả về dữ liệu hình ảnh cụ thể của tài nguyên PDF")
    public ResponseEntity<byte[]> extractImageResource(
            @PathVariable("id") Long id,
            @PathVariable("pageNumber") int pageNumber,
            @PathVariable("imageName") String imageName) {
        byte[] imageBytes = documentService.extractImageResource(id, pageNumber, imageName);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(imageBytes);
    }

    @PatchMapping("/{id}/threshold")
    @Operation(summary = "Cập nhật ngưỡng vector path và quét lại hình ảnh trong tài liệu")
    public ResponseEntity<DocumentResponse> updateThreshold(
            @PathVariable("id") Long id,
            @RequestParam("threshold") Integer threshold) {
        DocumentResponse response = documentService.updateVectorPathThreshold(id, threshold);
        return ResponseEntity.ok(response);
    }
}
