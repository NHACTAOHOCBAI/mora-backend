package com.mora.backend.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "benchmark_runs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenchmarkRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "approach_name", nullable = false)
    private String approachName;

    @Column(name = "ragas_faithfulness")
    private Double ragasFaithfulness;

    @Column(name = "ragas_answer_relevance")
    private Double ragasAnswerRelevance;

    @Column(name = "ragas_context_precision")
    private Double ragasContextPrecision;

    @Column(name = "ragas_context_recall")
    private Double ragasContextRecall;

    @Column(name = "avg_latency_ms")
    private Long avgLatencyMs;

    @CreationTimestamp
    @Column(name = "run_at", updatable = false)
    private LocalDateTime runAt;

    @OneToMany(mappedBy = "benchmarkRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BenchmarkRunDetail> details = new ArrayList<>();
}
