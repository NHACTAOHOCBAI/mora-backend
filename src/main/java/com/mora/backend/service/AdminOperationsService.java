package com.mora.backend.service;

import com.mora.backend.model.dto.response.AdminOperationsResponses;
import com.mora.backend.model.dto.response.PageResponse;
import com.mora.backend.model.entity.DocumentStatus;

public interface AdminOperationsService {
    AdminOperationsResponses.Dashboard dashboard(int rangeDays);
    PageResponse<AdminOperationsResponses.SpaceSummary> spaces(int page, int limit, String search, String sortBy, String sortOrder);
    AdminOperationsResponses.SpaceDetail spaceDetail(Long id);
    PageResponse<AdminOperationsResponses.DocumentSummary> documents(int page, int limit, String search, String sortBy, String sortOrder, DocumentStatus status, Long spaceId, Long uploaderId);
    AdminOperationsResponses.DocumentSummary documentDetail(Long id);
    AdminOperationsResponses.UserDetail userDetail(Long id);
}
