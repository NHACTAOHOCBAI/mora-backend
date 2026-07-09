package com.mora.backend.service.impl;

import com.mora.backend.config.RabbitMQConfig;
import com.mora.backend.model.dto.event.UserQuestionEvent;
import com.mora.backend.service.EventPublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisherServiceImpl implements EventPublisherService {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishUserQuestion(UserQuestionEvent event) {
        log.info("Publishing UserQuestionEvent to RabbitMQ: spaceId={}, userMessageId={}", 
                event.getSpaceId(), event.getUserMessageId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE, 
                RabbitMQConfig.USER_QUESTION_ROUTING_KEY, 
                event
        );
    }
}
