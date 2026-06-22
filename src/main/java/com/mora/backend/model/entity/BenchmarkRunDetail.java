package com.mora.backend.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "benchmark_run_details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkRunDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @JsonIgnore
    private BenchmarkRun benchmarkRun;

    @Lob
    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;

    @Lob
    @Column(name = "retrieved_contexts", columnDefinition = "TEXT")
    private String retrievedContexts;

    @Lob
    @Column(name = "generated_answer", columnDefinition = "TEXT")
    private String generatedAnswer;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "faithfulness")
    private Double faithfulness;

    @Column(name = "answer_relevance")
    private Double answerRelevance;

    @Column(name = "context_precision")
    private Double contextPrecision;

    @Column(name = "context_recall")
    private Double contextRecall;
}
