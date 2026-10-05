package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.entity.*;
import com.mora.backend.repository.DocumentPageRepository;
import com.mora.backend.repository.DocumentRepository;
import com.mora.backend.repository.SpaceMemberRepository;
import com.mora.backend.repository.SpaceRepository;
import com.mora.backend.service.DocumentService;
import com.mora.backend.service.StorageService;
import com.mora.backend.service.UserAiSettingService;
import com.mora.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository documentPageRepository;
    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final StorageService storageService;
    private final DocumentAsyncProcessor documentAsyncProcessor;
    private final UserService userService;
    private final UserAiSettingService userAiSettingService;

    @Override
    @Transactional
    public Document uploadDocument(Long spaceId, String name, byte[] content, String contentType) {
        log.info("Creating upload placeholder document: name={}, size={}, contentType={}, spaceId={}", name, content.length, contentType, spaceId);

        User currentUser = userService.getCurrentUser();
        UserAiSetting userSetting = userAiSettingService.getSettingForUser(currentUser);
        if (userSetting.getGeminiApiKey() == null || userSetting.getGeminiApiKey().isBlank()) {
            log.warn("User {} chưa cấu hình Gemini API Key khi tải tài liệu", currentUser.getUsername());
            throw new AppException(ErrorCode.GEMINI_API_KEY_REQUIRED);
        }

        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));

        SpaceRole userRole = getUserRole(space, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && (userRole == null || userRole == SpaceRole.VIEWER)) {
            log.warn("User {} has role {} in space {}, cannot upload documents", currentUser.getUsername(), userRole, spaceId);
            throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        // 1. Create and save document metadata with status UPLOADING
        Document doc = Document.builder()
                .name(name)
                .storageUrl("") // temp URL, updated in async process
                .fileSize((long) content.length)
                .contentType(contentType)
                .space(space)
                .uploadedBy(currentUser)
                .status(DocumentStatus.UPLOADING)
                .build();
        doc = documentRepository.save(doc);

        // 2. Trigger asynchronous processing
        documentAsyncProcessor.processDocumentAsync(
                doc.getId(), 
                spaceId, 
                content, 
                name, 
                contentType,
                currentUser.getId(),
                userSetting.getGeminiApiKey(),
                userSetting.getParserModel()
        );

        return doc;
    }

    @Override
    @Transactional(readOnly = true)
    public Document getDocumentById(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Document> getDocumentsBySpace(Long spaceId) {
        return documentRepository.findBySpaceId(spaceId);
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND));

        User currentUser = userService.getCurrentUser();
        Space space = doc.getSpace();
        SpaceRole userRole = getUserRole(space, currentUser);

        boolean isUploader = doc.getUploadedBy() != null && doc.getUploadedBy().getId().equals(currentUser.getId());
        boolean isOwner = userRole == SpaceRole.OWNER || currentUser.getRole() == Role.ROLE_ADMIN;

        if (!isOwner && !isUploader) {
            log.warn("User {} is neither owner nor uploader of doc {}, deletion denied", currentUser.getUsername(), documentId);
            throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        // 1. Delete from storage
        String storageUrl = doc.getStorageUrl();
        if (storageUrl != null && !storageUrl.isBlank()) {
            String fileKey = storageUrl.substring(storageUrl.lastIndexOf("/") + 1);
            storageService.deleteFile(fileKey);
        }

        // 2. Delete document pages and document from DB
        documentPageRepository.deleteByDocumentId(documentId);
        documentRepository.delete(doc);
        log.info("Deleted document and pages for ID: {}", documentId);
    }

    private SpaceRole getUserRole(Space space, User user) {
        if (user == null || space == null) return null;
        if (user.getRole() == Role.ROLE_ADMIN) return SpaceRole.OWNER;
        if (space.getUser() != null && space.getUser().getId().equals(user.getId())) return SpaceRole.OWNER;

        Optional<SpaceMember> memberOpt = spaceMemberRepository.findBySpaceIdAndUserId(space.getId(), user.getId());
        return memberOpt.map(SpaceMember::getRole).orElse(null);
    }
}
