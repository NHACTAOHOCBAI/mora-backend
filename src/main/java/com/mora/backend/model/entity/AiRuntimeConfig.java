package com.mora.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

/** Singleton control-plane configuration. The provider key is AES-GCM encrypted. */
@Entity
@Table(name = "ai_runtime_config")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AiRuntimeConfig {
    @Id private Long id;
    @Column(name = "encrypted_api_key", columnDefinition = "TEXT") private String encryptedApiKey;
    @Column(name = "key_fingerprint", length = 16) private String keyFingerprint;
    @Column(name = "encrypted_benchmark_api_key", columnDefinition = "TEXT") private String encryptedBenchmarkApiKey;
    @Column(name = "benchmark_key_fingerprint", length = 16) private String benchmarkKeyFingerprint;
    @Column(name = "chat_model", nullable = false, length = 128) private String chatModel;
    @Column(name = "parser_model", nullable = false, length = 128) private String parserModel;
    @Column(name = "embedding_model", nullable = false, length = 128) private String embeddingModel;
    // Nullable at schema level for a safe rollout over existing rows; runtime()
    // rejects incomplete settings until an administrator saves them.
    @Column(name = "benchmark_judge_model", length = 128) private String benchmarkJudgeModel;
    @Column(name = "benchmark_judge_embedding_model", length = 128) private String benchmarkJudgeEmbeddingModel;
    @Column(name = "vnd_per_usd", nullable = false) private double vndPerUsd;
    @Column(name = "pricing_version", nullable = false, length = 32) private String pricingVersion;
    @Column(name = "model_prices_json", nullable = false, columnDefinition = "TEXT") private String modelPricesJson;
    @Column(name = "updated_by_id") private Long updatedById;
    @UpdateTimestamp @Column(name = "updated_at") private LocalDateTime updatedAt;
}
