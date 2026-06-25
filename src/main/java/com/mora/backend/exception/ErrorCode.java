package com.mora.backend.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public enum ErrorCode {
    UNCATEGORIZED_EXCEPTION(9999, "Lỗi không xác định", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_KEY(1001, "Uncategorized error key", HttpStatus.BAD_REQUEST),
    USER_EXISTED(1002, "Người dùng đã tồn tại", HttpStatus.BAD_REQUEST),
    USERNAME_INVALID(1003, "Tên đăng nhập không hợp lệ", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(1004, "Mật khẩu không hợp lệ", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND(1005, "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    UNAUTHENTICATED(1006, "Không thể xác thực danh tính", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(1007, "Bạn không có quyền truy cập chức năng này", HttpStatus.FORBIDDEN),
    DOCUMENT_NOT_FOUND(1008, "Không tìm thấy tài liệu", HttpStatus.NOT_FOUND),
    FILE_UPLOAD_FAILED(1009, "Tải file lên hệ thống thất bại", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_FILE_FORMAT(1010, "Định dạng file không hợp lệ, chỉ hỗ trợ PDF và hình ảnh (PNG, JPG, JPEG)", HttpStatus.BAD_REQUEST),
    SPACE_NOT_FOUND(1011, "Không tìm thấy Không gian học tập", HttpStatus.NOT_FOUND),
    PDF_PROCESSING_FAILED(1012, "Lỗi bóc tách hoặc xử lý tài liệu PDF", HttpStatus.INTERNAL_SERVER_ERROR),
    AI_ENGINE_ERROR(1013, "Lỗi từ công cụ AI (Gemini Engine)", HttpStatus.INTERNAL_SERVER_ERROR),
    IMAGE_RESOURCES_NOT_FOUND(1014, "Không tìm thấy tài nguyên hình ảnh trong tài liệu", HttpStatus.NOT_FOUND),
    IMAGE_EXTRACTION_FAILED(1015, "Lỗi trích xuất hình ảnh từ tài liệu", HttpStatus.INTERNAL_SERVER_ERROR),
    BENCHMARK_QUESTION_NOT_FOUND(1016, "Không tìm thấy câu hỏi đánh giá", HttpStatus.NOT_FOUND),
    BENCHMARK_RUN_NOT_FOUND(1017, "Không tìm thấy kết quả đánh giá", HttpStatus.NOT_FOUND),
    OLD_PASSWORD_INCORRECT(1018, "Mật khẩu cũ không chính xác", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(1019, "Kích thước ảnh đại diện không được vượt quá 5MB", HttpStatus.BAD_REQUEST),
    BAD_CREDENTIALS(1020, "Tên đăng nhập hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED)
    ;

    private final int code;
    private final String message;
    private final HttpStatus statusCode;

    ErrorCode(int code, String message, HttpStatus statusCode) {
        this.code = code;
        this.message = message;
        this.statusCode = statusCode;
    }
}
