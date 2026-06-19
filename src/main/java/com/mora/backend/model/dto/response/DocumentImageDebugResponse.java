package com.mora.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentImageDebugResponse {
    private Integer pageNumber;
    private String pageContent;
    private List<ImageInfo> images;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageInfo {
        private String name;
        private String type;
        private int width;
        private int height;
        private boolean accepted;
        private String filterReason;
    }
}
