package com.mora.backend.benchmark;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class BenchmarkSample {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
private Long runId;
private String questionCode;
private String questionType;
private int repetition;
private String status;
@Column(columnDefinition="TEXT") private String traceJson;
@Column(columnDefinition="TEXT") private String metricsJson;
private String error;
}
