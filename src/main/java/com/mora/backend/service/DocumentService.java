package com.mora.backend.service;

import com.mora.backend.model.dto.response.DocumentDetailResponse;
import com.mora.backend.model.dto.response.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {
    /**
     * Upload tài liệu lên Cloud và bóc tách văn bản từng trang để lưu vào CSDL.
     *
     * @param file File tải lên
     * @return DTO chứa thông tin tài liệu đã lưu
     */
    DocumentResponse uploadAndProcessDocument(MultipartFile file);

    /**
     * Lấy thông tin chi tiết tài liệu kèm nội dung các trang.
     *
     * @param id ID của tài liệu
     * @return DTO chi tiết tài liệu
     */
    DocumentDetailResponse getDocumentById(Long id);

    /**
     * Xóa tài liệu khỏi hệ thống lưu trữ Cloud và Cơ sở dữ liệu.
     *
     * @param id ID của tài liệu cần xóa
     */
    void deleteDocument(Long id);
}
