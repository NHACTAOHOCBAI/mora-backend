package com.mora.backend.service;

public interface StorageService {
    String uploadFile(String key, byte[] content, String contentType);
    void deleteFile(String key);
}
