package com.mora.backend.service;

import com.mora.backend.model.dto.request.GroupChatMessageRequest;
import com.mora.backend.model.dto.request.SpaceChatRequest;
import com.mora.backend.model.dto.response.ChatMessageResponse;
import com.mora.backend.model.dto.response.SpaceChatResponse;

import java.util.List;

public interface ChatService {
    /**
     * Gửi tin nhắn trao đổi thông thường giữa các thành viên trong Không gian học tập (người-người).
     *
     * @param request DTO chứa ID Space và nội dung tin nhắn
     * @return DTO chứa thông tin tin nhắn đã gửi
     */
    ChatMessageResponse sendGroupMessage(GroupChatMessageRequest request);

    /**
     * Hỏi đáp RAG với toàn bộ không gian học tập hoặc danh sách tài liệu chọn lọc (@AI Trigger).
     *
     * @param request DTO chứa ID Space, câu hỏi và documentIds tùy chọn
     * @return DTO chứa câu trả lời và các trích dẫn
     */
    SpaceChatResponse chatWithSpace(SpaceChatRequest request);

    /**
     * Lấy toàn bộ lịch sử cuộc trò chuyện (cả tin nhắn nhóm và phản hồi AI) của một Không gian học tập.
     *
     * @param spaceId ID Space
     * @return Danh sách tin nhắn
     */
    List<ChatMessageResponse> getSpaceChatHistory(Long spaceId);

    /**
     * Xóa lịch sử cuộc trò chuyện của một Không gian học tập (Chỉ OWNER hoặc ADMIN).
     *
     * @param spaceId ID Space
     */
    void clearSpaceChatHistory(Long spaceId);
}
