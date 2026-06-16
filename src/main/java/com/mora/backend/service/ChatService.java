package com.mora.backend.service;

import com.mora.backend.model.dto.request.DocumentChatRequest;
import com.mora.backend.model.dto.request.SpaceChatRequest;
import com.mora.backend.model.dto.response.DocumentChatResponse;
import com.mora.backend.model.dto.response.SpaceChatResponse;

public interface ChatService {
    /**
     * Hỏi đáp với tài liệu dựa trên câu hỏi và ID tài liệu được cung cấp.
     *
     * @param request DTO chứa ID tài liệu và câu hỏi
     * @return DTO chứa câu trả lời và các trích dẫn trang
     */
    DocumentChatResponse chatWithDocument(DocumentChatRequest request);

    /**
     * Hỏi đáp với toàn bộ không gian học tập (nhiều tài liệu) dựa trên câu hỏi và ID Space được cung cấp.
     *
     * @param request DTO chứa ID Space và câu hỏi
     * @return DTO chứa câu trả lời và các trích dẫn thuộc các tài liệu khác nhau
     */
    SpaceChatResponse chatWithSpace(SpaceChatRequest request);
}
