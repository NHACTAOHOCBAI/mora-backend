package com.mora.backend.service;

import com.mora.backend.model.dto.event.UserQuestionEvent;

public interface EventPublisherService {
    void publishUserQuestion(UserQuestionEvent event);
}
