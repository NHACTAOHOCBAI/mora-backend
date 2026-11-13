package com.mora.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "quiz_questions")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QuizQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "study_set_id", nullable = false) private StudySet studySet;
    @Column(nullable = false, columnDefinition = "TEXT") private String question;
    @Column(nullable = false, columnDefinition = "TEXT") private String optionsJson;
    @Column(name = "correct_option_index", nullable = false) private Integer correctOptionIndex;
    @Column(nullable = false, columnDefinition = "TEXT") private String explanation;
    @Column(name = "document_id") private Long documentId;
    @Column(name = "document_name") private String documentName;
    @Column(name = "page_number") private Integer pageNumber;
    @Column(name = "source_quote", columnDefinition = "TEXT") private String sourceQuote;
}
