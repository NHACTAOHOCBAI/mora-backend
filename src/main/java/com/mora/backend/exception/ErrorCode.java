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
    SPACE_NOT_FOUND(1011, "Không tìm thấy Không gian học tập", HttpStatus.NOT_FOUND)
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
