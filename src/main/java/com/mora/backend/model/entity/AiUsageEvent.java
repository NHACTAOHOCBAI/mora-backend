package com.mora.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ai_usage_events", indexes = {@Index(name = "idx_ai_usage_created_at", columnList = "created_at"), @Index(name = "idx_ai_usage_operation", columnList = "operation")})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AiUsageEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 64) private String operation;
    @Column(nullable = false, length = 128) private String model;
    private long inputTokens;
    private long outputTokens;
    private long cachedInputTokens;
    private long totalTokens;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal estimatedCostVnd;
    @Column(length = 32) private String pricingVersion;
    @Column(name = "space_id") private Long spaceId;
    @Column(name = "document_id") private Long documentId;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
}
