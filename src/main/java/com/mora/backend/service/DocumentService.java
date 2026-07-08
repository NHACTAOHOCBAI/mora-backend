package com.mora.backend.service;

import com.mora.backend.model.entity.Document;
import java.util.List;

public interface DocumentService {
    Document uploadDocument(Long spaceId, String name, byte[] content, String contentType);
    Document getDocumentById(Long documentId);
    List<Document> getDocumentsBySpace(Long spaceId);
    void deleteDocument(Long documentId);
}
