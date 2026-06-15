package com.mora.backend.service;

import com.mora.backend.model.dto.request.DocumentChatRequest;
import com.mora.backend.model.dto.response.DocumentChatResponse;

public interface ChatService {
    /**
     * Hỏi đáp với tài liệu dựa trên câu hỏi và ID tài liệu được cung cấp.
     *
     * @param request DTO chứa ID tài liệu và câu hỏi
     * @return DTO chứa câu trả lời và các trích dẫn trang
     */
    DocumentChatResponse chatWithDocument(DocumentChatRequest request);
}
