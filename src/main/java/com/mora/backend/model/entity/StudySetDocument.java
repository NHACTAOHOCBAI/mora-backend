package com.mora.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "study_set_documents", uniqueConstraints = @UniqueConstraint(columnNames = {"study_set_id", "document_id"}))
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StudySetDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "study_set_id", nullable = false) private StudySet studySet;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "document_id", nullable = false) private Document document;
}
