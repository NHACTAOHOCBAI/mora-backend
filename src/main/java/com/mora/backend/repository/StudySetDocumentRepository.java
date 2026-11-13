package com.mora.backend.repository;
import com.mora.backend.model.entity.StudySetDocument;
import org.springframework.data.jpa.repository.*;
public interface StudySetDocumentRepository extends JpaRepository<StudySetDocument, Long> { @Modifying void deleteByDocumentId(Long documentId); }
