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
public class PageResponse<T> {
    private List<T> data;
    private Meta meta;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Meta {
        private Pagination pagination;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Pagination {
        private long total;
        private int page;
        private int limit;
    }
}
