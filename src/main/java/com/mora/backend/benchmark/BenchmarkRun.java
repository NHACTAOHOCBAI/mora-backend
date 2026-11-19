package com.mora.backend.benchmark;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class BenchmarkRun {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
private Long datasetId;
private String status;
private boolean smoke;
private java.time.Instant createdAt;
@Column(columnDefinition="TEXT",nullable=false) private String snapshotJson;
@Column(columnDefinition="TEXT",nullable=false) private String configJson;
}
