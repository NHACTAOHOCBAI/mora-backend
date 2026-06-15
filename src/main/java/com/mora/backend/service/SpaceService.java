package com.mora.backend.service;

import com.mora.backend.model.dto.request.SpaceCreateRequest;
import com.mora.backend.model.dto.response.SpaceDetailResponse;
import com.mora.backend.model.dto.response.SpaceResponse;

import java.util.List;

public interface SpaceService {
    SpaceResponse createSpace(SpaceCreateRequest request);
    List<SpaceResponse> getAllSpaces();
    SpaceDetailResponse getSpaceById(Long id);
    void deleteSpace(Long id);
}
