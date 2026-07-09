package com.mora.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.mora.backend.model.entity.DocumentStatus;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private Long id;
    private String name;
    private String storageUrl;
    private Long fileSize;
    private String contentType;
    private DocumentStatus status;
    private Long spaceId;
    private LocalDateTime createdAt;
}
