package com.mora.backend.model.dto.response;

import com.mora.backend.model.entity.SpaceRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpaceDetailResponse {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private SpaceRole currentUserRole;
    private int memberCount;
    private List<DocumentResponse> documents;
    private List<SpaceMemberResponse> members;
    private String ownerName;
}
