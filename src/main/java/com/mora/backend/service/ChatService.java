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

    /**
     * Lấy lịch sử cuộc trò chuyện của một tài liệu.
     *
     * @param documentId ID tài liệu
     * @return Danh sách tin nhắn
     */
    java.util.List<com.mora.backend.model.dto.response.ChatMessageResponse> getDocumentChatHistory(Long documentId);

    /**
     * Lấy lịch sử cuộc trò chuyện của một Không gian học tập.
     *
     * @param spaceId ID Space
     * @return Danh sách tin nhắn
     */
    java.util.List<com.mora.backend.model.dto.response.ChatMessageResponse> getSpaceChatHistory(Long spaceId);

    /**
     * Xóa lịch sử cuộc trò chuyện của một tài liệu.
     *
     * @param documentId ID tài liệu
     */
    void clearDocumentChatHistory(Long documentId);

    /**
     * Xóa lịch sử cuộc trò chuyện của một Không gian học tập.
     *
     * @param spaceId ID Space
     */
    void clearSpaceChatHistory(Long spaceId);
}

