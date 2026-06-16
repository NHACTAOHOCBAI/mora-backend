package com.mora.backend.repository;

import com.mora.backend.model.entity.DocumentPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentPageRepository extends JpaRepository<DocumentPage, Long> {
    List<DocumentPage> findByDocumentIdOrderByPageNumberAsc(Long documentId);

    @Query("SELECT dp FROM DocumentPage dp JOIN FETCH dp.document d WHERE d.space.id = :spaceId ORDER BY d.id ASC, dp.pageNumber ASC")
    List<DocumentPage> findBySpaceId(@Param("spaceId") Long spaceId);
}
