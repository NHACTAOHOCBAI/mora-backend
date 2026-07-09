package com.mora.backend.service.impl;

import com.mora.backend.client.AiServiceClient;
import com.mora.backend.model.entity.Space;
import com.mora.backend.repository.SpaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSummaryHelper {

    private final SpaceRepository spaceRepository;
    private final AiServiceClient aiServiceClient;

    @Async
    @Transactional
    public void updateSpaceChatSummary(Long spaceId, List<AiServiceClient.PythonChatRequest.HistoryItem> history) {
        log.info("Bắt đầu tiến trình chạy ngầm tóm tắt cuộc hội thoại cho Space ID: {}", spaceId);
        try {
            Space space = spaceRepository.findById(spaceId).orElse(null);
            if (space == null) {
                log.warn("Không tìm thấy Space ID: {} để thực hiện tóm tắt", spaceId);
                return;
            }

            AiServiceClient.PythonSummarizeRequest request = new AiServiceClient.PythonSummarizeRequest();
            request.history = history;
            request.previousSummary = space.getChatSummary();

            AiServiceClient.PythonSummarizeResponse response = aiServiceClient.callSummarize(request);
            if (response != null && response.summary != null) {
                space.setChatSummary(response.summary);
                spaceRepository.save(space);
                log.info("Cập nhật tóm tắt hội thoại thành công cho Space ID: {}", spaceId);
            }
        } catch (Exception e) {
            log.error("Lỗi khi chạy ngầm tóm tắt cuộc hội thoại cho Space ID: {}", spaceId, e);
        }
    }
}
