package com.mora.backend.service;

import com.mora.backend.model.dto.response.DocumentDetailResponse;
import com.mora.backend.model.dto.response.DocumentResponse;
import com.mora.backend.model.dto.response.DocumentImageDebugResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface DocumentService {
    /**
     * Upload tài liệu lên Cloud và bóc tách văn bản từng trang để lưu vào CSDL.
     *
     * @param file File tải lên
     * @param spaceId ID của Space chứa tài liệu
     * @param vectorPathThreshold Ngưỡng đếm vector path để phát hiện ảnh vector
     * @return DTO chứa thông tin tài liệu đã lưu
     */
    DocumentResponse uploadAndProcessDocument(MultipartFile file, Long spaceId, Integer vectorPathThreshold);

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

    /**
     * Trích xuất chi tiết thông tin ảnh đồ họa từng trang phục vụ debug.
     *
     * @param id ID tài liệu
     * @return Danh sách thông tin ảnh từng trang
     */
    List<DocumentImageDebugResponse> debugDocumentImages(Long id);

    /**
     * Trích xuất trực tiếp tài nguyên hình ảnh (PDImageXObject) từ tệp PDF.
     *
     * @param documentId ID của tài liệu
     * @param pageNumber Số trang
     * @param imageName Tên của đối tượng ảnh (XObject name)
     * @return Byte array của hình ảnh dạng PNG
     */
    byte[] extractImageResource(Long documentId, int pageNumber, String imageName);

    /**
     * Cập nhật ngưỡng Vector Path cho tài liệu và quét lại hình ảnh các trang.
     *
     * @param id ID của tài liệu
     * @param threshold Ngưỡng mới
     * @return DTO chứa thông tin tài liệu đã cập nhật
     */
    DocumentResponse updateVectorPathThreshold(Long id, Integer threshold);
}
