package com.mora.backend.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "mora.direct.exchange";
    
    public static final String USER_QUESTION_QUEUE = "mora.queue.user-question";
    public static final String ANSWER_VERIFIED_QUEUE = "mora.queue.answer-verified";
    
    public static final String USER_QUESTION_ROUTING_KEY = "mora.route.user-question";
    public static final String ANSWER_VERIFIED_ROUTING_KEY = "mora.route.answer-verified";

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Queue userQuestionQueue() {
        return QueueBuilder.durable(USER_QUESTION_QUEUE).build();
    }

    @Bean
    public Queue answerVerifiedQueue() {
        return QueueBuilder.durable(ANSWER_VERIFIED_QUEUE).build();
    }

    @Bean
    public Binding bindingUserQuestion(Queue userQuestionQueue, DirectExchange exchange) {
        return BindingBuilder.bind(userQuestionQueue).to(exchange).with(USER_QUESTION_ROUTING_KEY);
    }

    @Bean
    public Binding bindingAnswerVerified(Queue answerVerifiedQueue, DirectExchange exchange) {
        return BindingBuilder.bind(answerVerifiedQueue).to(exchange).with(ANSWER_VERIFIED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
