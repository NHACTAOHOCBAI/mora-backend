package com.mora.backend.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /**
     * Tải file lên và trả về đường dẫn URL công khai để truy cập file.
     *
     * @param file File cần tải lên
     * @return URL của file
     */
    String upload(MultipartFile file);

    /**
     * Xóa file khỏi hệ thống lưu trữ.
     *
     * @param fileName Tên file cần xóa
     */
    void delete(String fileName);
}
