package com.mora.backend.benchmark;
import jakarta.persistence.*;
import lombok.*;
@Entity @Getter @Setter @NoArgsConstructor
public class BenchmarkQuestion {
@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
@Column(nullable=false) private Long datasetId;
@Column(nullable=false) private String code;
@Column(columnDefinition="TEXT",nullable=false) private String definitionJson;
}
