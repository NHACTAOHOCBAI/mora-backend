package com.mora.backend.repository;

import com.mora.backend.model.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySpaceIdOrderByCreatedAtAsc(Long spaceId);

    long countBySpaceIdAndSender(Long spaceId, String sender);

    void deleteBySpaceId(Long spaceId);
}
