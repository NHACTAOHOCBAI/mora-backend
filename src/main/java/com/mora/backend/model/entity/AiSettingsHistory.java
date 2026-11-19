package com.mora.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ai_settings_history")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class AiSettingsHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Instant savedAt;
    @Column(nullable = false)
    private Long actorId;
    @Column(nullable = false)
    private String actorName;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String changesJson;
}
