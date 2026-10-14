package com.mora.backend.exception;

import com.mora.backend.model.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Bắt toàn bộ lỗi chưa được phân loại khác
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ApiResponse<Object>> handlingRuntimeException(Exception exception) {
        log.error("Unhandled API exception", exception);
        return error(ErrorCode.UNCATEGORIZED_EXCEPTION);
    }

    // Bắt lỗi nghiệp vụ được định nghĩa sẵn
    @ExceptionHandler(value = AppException.class)
    public ResponseEntity<ApiResponse<Object>> handlingAppException(AppException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        return error(errorCode);
    }

    // Bắt lỗi Validation đầu vào (@Valid)
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handlingValidation(MethodArgumentNotValidException exception) {
        if (exception.getFieldError() == null) {
            return error(ErrorCode.INVALID_REQUEST_PARAMETER);
        }
        String enumKey = exception.getFieldError().getDefaultMessage();
        ErrorCode errorCode = ErrorCode.INVALID_KEY;
        try {
            errorCode = ErrorCode.valueOf(enumKey);
        } catch (IllegalArgumentException e) {
            // Sử dụng message mặc định từ annotation nếu không tìm thấy key trong enum
        }

        if (errorCode == ErrorCode.INVALID_KEY) {
            ApiResponse<Object> response = new ApiResponse<>();
            response.setCode(errorCode.getCode());
            response.setMessage(exception.getFieldError().getDefaultMessage());
            return ResponseEntity.status(errorCode.getStatusCode()).body(response);
        }
        return error(errorCode);
    }

    // Bắt lỗi upload file vượt quá kích thước cho phép
    @ExceptionHandler(value = MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception) {
        ErrorCode errorCode = ErrorCode.DOCUMENT_TOO_LARGE;
        return error(errorCode);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Object>> handleMalformedRequest(Exception exception) {
        return error(exception instanceof MethodArgumentTypeMismatchException
                ? ErrorCode.INVALID_REQUEST_PARAMETER : ErrorCode.MALFORMED_REQUEST);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Object>> handleMissingRequestData(Exception exception) {
        return error(ErrorCode.MISSING_REQUEST_DATA);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException exception) {
        return error(ErrorCode.UNAUTHORIZED);
    }

    private ResponseEntity<ApiResponse<Object>> error(ErrorCode errorCode) {
        ApiResponse<Object> response = new ApiResponse<>();
        response.setCode(errorCode.getCode());
        response.setMessage(errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatusCode()).body(response);
    }
}
