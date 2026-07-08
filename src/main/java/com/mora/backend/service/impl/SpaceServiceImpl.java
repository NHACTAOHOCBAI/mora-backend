package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.request.SpaceCreateRequest;
import com.mora.backend.model.dto.response.SpaceDetailResponse;
import com.mora.backend.model.dto.response.SpaceResponse;
import com.mora.backend.model.entity.Role;
import com.mora.backend.model.entity.Space;
import com.mora.backend.model.entity.User;
import com.mora.backend.repository.SpaceRepository;
import com.mora.backend.repository.ChatMessageRepository;
import com.mora.backend.service.SpaceService;
import com.mora.backend.service.UserService;
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
    private final ChatMessageRepository chatMessageRepository;
    private final UserService userService;

    @Override
    @Transactional
    public SpaceResponse createSpace(SpaceCreateRequest request) {
        User currentUser = userService.getCurrentUser();
        Space space = Space.builder()
                .name(request.getName())
                .description(request.getDescription())
                .user(currentUser)
                .build();
        
        space = spaceRepository.save(space);
        
        return convertToSpaceResponse(space);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaceResponse> getAllSpaces() {
        User currentUser = userService.getCurrentUser();
        List<Space> spaces;
        if (currentUser.getRole() == Role.ROLE_ADMIN) {
            spaces = spaceRepository.findAll();
        } else {
            spaces = spaceRepository.findByUser(currentUser);
        }
        return spaces.stream()
                .map(this::convertToSpaceResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SpaceDetailResponse getSpaceById(Long id) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Space with ID {} not found", id);
                    return new AppException(ErrorCode.SPACE_NOT_FOUND);
                });

        // Enforce ownership
        if (currentUser.getRole() != Role.ROLE_ADMIN && (space.getUser() == null || !space.getUser().getId().equals(currentUser.getId()))) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        return SpaceDetailResponse.builder()
                .id(space.getId())
                .name(space.getName())
                .description(space.getDescription())
                .createdAt(space.getCreatedAt())
                .updatedAt(space.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public void deleteSpace(Long id) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Space with ID {} not found for deletion", id);
                    return new AppException(ErrorCode.SPACE_NOT_FOUND);
                });

        // Enforce ownership
        if (currentUser.getRole() != Role.ROLE_ADMIN && (space.getUser() == null || !space.getUser().getId().equals(currentUser.getId()))) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // 1. Delete space-level chat messages
        chatMessageRepository.deleteBySpaceId(id);

        // 2. Delete the space itself
        spaceRepository.delete(space);
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
