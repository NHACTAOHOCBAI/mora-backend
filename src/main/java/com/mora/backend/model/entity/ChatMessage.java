package com.mora.backend.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender", nullable = false)
    private String sender; // "user" hoặc "assistant"

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id")
    private Space space;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(name = "condensed_question", columnDefinition = "TEXT")
    private String condensedQuestion;

    @Column(name = "prompt_sent", columnDefinition = "TEXT")
    private String promptSent;

    @Column(name = "citations", columnDefinition = "TEXT")
    private String citations; // JSON string chứa danh sách Citation

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 30)
    @Builder.Default
    private MessageType messageType = MessageType.USER_MESSAGE;

    @Column(name = "selected_document_ids", columnDefinition = "TEXT")
    private String selectedDocumentIds; // JSON string [1, 2]

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
