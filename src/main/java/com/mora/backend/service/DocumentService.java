package com.mora.backend.service;

import com.mora.backend.model.dto.response.DocumentDetailResponse;
import com.mora.backend.model.dto.response.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {
    /**
     * Upload tài liệu lên Cloud và bóc tách văn bản từng trang để lưu vào CSDL.
     *
     * @param file File tải lên
     * @param spaceId ID của Space chứa tài liệu
     * @return DTO chứa thông tin tài liệu đã lưu
     */
    DocumentResponse uploadAndProcessDocument(MultipartFile file, Long spaceId);

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

    /**
     * Tạo tóm tắt và câu hỏi ôn tập (Flashcards) tự động cho tài liệu bằng AI.
     *
     * @param id ID của tài liệu
     * @return DTO chi tiết tài liệu chứa tóm tắt và flashcard mới sinh
     */
    DocumentDetailResponse generateStudyNotes(Long id);

    /**
     * Đổi tên tài liệu.
     *
     * @param id ID của tài liệu
     * @param newName Tên mới của tài liệu
     * @return DTO chứa thông tin tài liệu đã cập nhật
     */
    DocumentResponse renameDocument(Long id, String newName);

    /**
     * Kết xuất trang PDF hoặc ảnh gốc và trả về byte array hình ảnh đã được tối ưu hóa.
     *
     * @param documentId ID của tài liệu
     * @param pageNumber Số trang
     * @return Byte array của ảnh (JPEG) đã tối ưu
     */
    byte[] renderPageImage(Long documentId, int pageNumber);
}
