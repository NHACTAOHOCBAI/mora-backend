package com.mora.backend.repository;

import com.mora.backend.model.entity.DocumentPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentPageRepository extends JpaRepository<DocumentPage, Long> {
    List<DocumentPage> findByDocumentIdOrderByPageNumberAsc(Long documentId);
}
