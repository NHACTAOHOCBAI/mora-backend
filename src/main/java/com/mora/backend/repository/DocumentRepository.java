package com.mora.backend.repository;

import com.mora.backend.model.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.mora.backend.model.entity.DocumentStatus;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findBySpaceId(Long spaceId);

    @Query("SELECT d FROM Document d WHERE (:search IS NULL OR :search = '' OR LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR d.status = :status) AND (:spaceId IS NULL OR d.space.id = :spaceId) " +
            "AND (:uploaderId IS NULL OR d.uploadedBy.id = :uploaderId)")
    Page<Document> searchForAdmin(@Param("search") String search, @Param("status") DocumentStatus status,
                                  @Param("spaceId") Long spaceId, @Param("uploaderId") Long uploaderId, Pageable pageable);

    List<Document> findByUploadedById(Long uploadedById);
}
