package com.mora.backend.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "document_pages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "document")
public class DocumentPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    @JsonIgnore
    private Document document;

    @Column(name = "page_number", nullable = false)
    private Integer pageNumber;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "has_image")
    private Boolean hasImage;
}
