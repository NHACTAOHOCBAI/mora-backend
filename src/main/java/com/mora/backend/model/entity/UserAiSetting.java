package com.mora.backend.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_ai_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAiSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "gemini_api_key", length = 500)
    private String geminiApiKey;

    @Column(name = "chat_model", length = 100)
    @Builder.Default
    private String chatModel = "gemini-2.5-flash";

    @Column(name = "router_model", length = 100)
    @Builder.Default
    private String routerModel = "gemini-3.1-flash-lite";

    @Column(name = "evaluator_model", length = 100)
    @Builder.Default
    private String evaluatorModel = "gemini-3.1-flash-lite";

    @Column(name = "parser_model", length = 100)
    @Builder.Default
    private String parserModel = "gemini-3.5-flash-lite";

    @Column(name = "summarizer_model", length = 100)
    @Builder.Default
    private String summarizerModel = "gemini-3.1-flash-lite";

    @Column(name = "temperature")
    @Builder.Default
    private Double temperature = 0.0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
