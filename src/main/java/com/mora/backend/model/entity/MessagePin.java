package com.mora.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.LocalDateTime;

@Entity
@Table(name = "message_pins")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MessagePin {
    @Id
    private Long messageId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChatMessage message;

    @Column(nullable = false)
    private Long pinnedByUserId;

    @Column(nullable = false)
    private String pinnedByName;

    @Column(nullable = false)
    private LocalDateTime pinnedAt;
}
