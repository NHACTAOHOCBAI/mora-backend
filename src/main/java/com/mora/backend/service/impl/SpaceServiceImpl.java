package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.request.SpaceCreateRequest;
import com.mora.backend.model.dto.response.DocumentResponse;
import com.mora.backend.model.dto.response.SpaceDetailResponse;
import com.mora.backend.model.dto.response.SpaceResponse;
import com.mora.backend.model.entity.Space;
import com.mora.backend.repository.SpaceRepository;
import com.mora.backend.service.SpaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpaceServiceImpl implements SpaceService {

    private final SpaceRepository spaceRepository;

    @Override
    @Transactional
    public SpaceResponse createSpace(SpaceCreateRequest request) {
        log.info("Creating a new space with name: {}", request.getName());
        
        Space space = Space.builder()
                .name(request.getName())
                .description(request.getDescription())
                .build();
        
        space = spaceRepository.save(space);
        log.info("Successfully created space with ID: {}", space.getId());
        
        return convertToSpaceResponse(space);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaceResponse> getAllSpaces() {
        log.info("Fetching all spaces");
        return spaceRepository.findAll().stream()
                .map(this::convertToSpaceResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SpaceDetailResponse getSpaceById(Long id) {
        log.info("Fetching details of space ID: {}", id);
        
        Space space = spaceRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Space with ID {} not found", id);
                    return new AppException(ErrorCode.SPACE_NOT_FOUND);
                });

        List<DocumentResponse> documentResponses = space.getDocuments().stream()
                .map(doc -> DocumentResponse.builder()
                        .id(doc.getId())
                        .fileName(doc.getFileName())
                        .fileType(doc.getFileType())
                        .storageUrl(doc.getStorageUrl())
                        .createdAt(doc.getCreatedAt())
                        .updatedAt(doc.getUpdatedAt())
                        .build())
                .toList();

        return SpaceDetailResponse.builder()
                .id(space.getId())
                .name(space.getName())
                .description(space.getDescription())
                .createdAt(space.getCreatedAt())
                .updatedAt(space.getUpdatedAt())
                .documents(documentResponses)
                .build();
    }

    @Override
    @Transactional
    public void deleteSpace(Long id) {
        log.info("Deleting space ID: {}", id);
        
        Space space = spaceRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Space with ID {} not found for deletion", id);
                    return new AppException(ErrorCode.SPACE_NOT_FOUND);
                });

        spaceRepository.delete(space);
        log.info("Successfully deleted space with ID: {}", id);
    }

    private SpaceResponse convertToSpaceResponse(Space space) {
        return SpaceResponse.builder()
                .id(space.getId())
                .name(space.getName())
                .description(space.getDescription())
                .createdAt(space.getCreatedAt())
                .updatedAt(space.getUpdatedAt())
                .build();
    }
}
