package com.mora.backend.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "study_sets")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class StudySet {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 255) private String title;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "space_id", nullable = false) private Space space;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by_id") private User createdBy;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @OneToMany(mappedBy = "studySet", cascade = CascadeType.ALL, orphanRemoval = true) @Builder.Default
    private List<StudySetDocument> sourceDocuments = new ArrayList<>();
    @OneToMany(mappedBy = "studySet", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("id ASC") @Builder.Default
    private List<Flashcard> flashcards = new ArrayList<>();
    @OneToMany(mappedBy = "studySet", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("id ASC") @Builder.Default
    private List<QuizQuestion> quizQuestions = new ArrayList<>();
}
