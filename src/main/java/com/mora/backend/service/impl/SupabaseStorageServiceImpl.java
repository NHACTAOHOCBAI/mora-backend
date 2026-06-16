package com.mora.backend.service.impl;

import com.mora.backend.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
public class SupabaseStorageServiceImpl implements StorageService {

    private final RestClient restClient;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    @Value("${supabase.bucket}")
    private String bucketName;

    public SupabaseStorageServiceImpl() {
        this.restClient = RestClient.builder().build();
    }

    @Override
    public String upload(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String cleanedFilename = originalFilename != null ? originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_") : "file";
        String uniqueFilename = UUID.randomUUID().toString() + "_" + cleanedFilename;

        // API Endpoint: POST {supabaseUrl}/storage/v1/object/{bucket}/{filename}
        String uploadUrl = String.format("%s/storage/v1/object/%s/%s", supabaseUrl, bucketName, uniqueFilename);

        try {
            byte[] fileBytes = file.getBytes();

            restClient.post()
                    .uri(uploadUrl)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .contentType(MediaType.parseMediaType(file.getContentType() != null ? file.getContentType() : "application/octet-stream"))
                    .body(fileBytes)
                    .retrieve()
                    .toBodilessEntity();

            // Public URL: {supabaseUrl}/storage/v1/object/public/{bucket}/{filename}
            String publicUrl = String.format("%s/storage/v1/object/public/%s/%s", supabaseUrl, bucketName, uniqueFilename);
            return publicUrl;

        } catch (IOException e) {
            log.error("Failed to read bytes from file: {}", originalFilename, e);
            throw new RuntimeException("Lỗi đọc file tải lên: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to upload file to Supabase: {}", originalFilename, e);
            throw new RuntimeException("Lỗi tải file lên Supabase Storage: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String fileName) {
        String deleteUrl = String.format("%s/storage/v1/object/%s/%s", supabaseUrl, bucketName, fileName);
        try {
            restClient.delete()
                    .uri(deleteUrl)
                    .header("Authorization", "Bearer " + supabaseKey)
                    .header("apikey", supabaseKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to delete file '{}' from Supabase Storage", fileName, e);
        }
    }
}
