package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@Service
@Slf4j
public class R2StorageServiceImpl implements StorageService {

    private final S3Client s3Client;
    private final String bucketName;
    private final String publicUrl;

    public R2StorageServiceImpl(
            @Value("${r2.endpoint}") String endpoint,
            @Value("${r2.access-key}") String accessKey,
            @Value("${r2.secret-key}") String secretKey,
            @Value("${r2.bucket}") String bucketName,
            @Value("${r2.public-url}") String publicUrl) {
        this.bucketName = bucketName;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl : publicUrl + "/";
        
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.US_EAST_1) // Region defaults to US_EAST_1 for S3 API compat
                .build();
    }

    @Override
    public String upload(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String cleanedFilename = originalFilename != null ? originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_") : "file";
        String uniqueFilename = UUID.randomUUID().toString() + "_" + cleanedFilename;

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(uniqueFilename)
                    .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            return publicUrl + uniqueFilename;
        } catch (IOException e) {
            log.error("Failed to read bytes from file: {}", originalFilename, e);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        } catch (Exception e) {
            log.error("Failed to upload file to Cloudflare R2: {}", originalFilename, e);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public void delete(String fileName) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build();
            s3Client.deleteObject(deleteObjectRequest);
            log.info("Successfully deleted file '{}' from Cloudflare R2 Storage", fileName);
        } catch (Exception e) {
            log.error("Failed to delete file '{}' from Cloudflare R2 Storage due to unexpected error", fileName, e);
        }
    }
}
