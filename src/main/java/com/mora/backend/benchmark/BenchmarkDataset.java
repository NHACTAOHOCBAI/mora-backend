package com.mora.backend.benchmark;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class BenchmarkDataset {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
@Column(nullable=false) private String name;
@Column(columnDefinition="TEXT",nullable=false) private String definitionJson;
private Long spaceId;
private java.time.Instant updatedAt;
}
