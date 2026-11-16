package com.mora.backend.repository;

import com.mora.backend.model.entity.MessagePin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MessagePinRepository extends JpaRepository<MessagePin, Long> {
    @Query("SELECT p FROM MessagePin p JOIN FETCH p.message m LEFT JOIN FETCH m.user WHERE m.space.id = :spaceId ORDER BY p.pinnedAt DESC, p.messageId DESC")
    List<MessagePin> findAllInSpace(@Param("spaceId") Long spaceId);
}
