package com.mora.backend.service.impl;

import com.mora.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageServiceImpl implements StorageService {

    private final S3Client s3Client;

    @Value("${r2.bucket}")
    private String bucketName;

    @Value("${r2.public-url}")
    private String publicUrl;

    @Override
    public String uploadFile(String key, byte[] content, String contentType) {
        log.info("Uploading file to R2: key={}, size={} bytes, contentType={}", key, content.length, contentType);
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));

            String fileUrl = publicUrl.endsWith("/") ? publicUrl + key : publicUrl + "/" + key;
            log.info("File uploaded successfully. URL: {}", fileUrl);
            return fileUrl;
        } catch (Exception e) {
            log.error("Failed to upload file to R2 storage", e);
            throw new RuntimeException("Lỗi tải tệp lên Cloud Storage: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String key) {
        log.info("Deleting file from R2: key={}", key);
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
            log.info("File deleted successfully.");
        } catch (Exception e) {
            log.error("Failed to delete file from R2 storage", e);
        }
    }
}
