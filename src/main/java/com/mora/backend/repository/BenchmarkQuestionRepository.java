package com.mora.backend.repository;

import com.mora.backend.model.entity.BenchmarkQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BenchmarkQuestionRepository extends JpaRepository<BenchmarkQuestion, Long> {
    @Query("SELECT q FROM BenchmarkQuestion q WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(q.question) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(q.groundTruth) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<BenchmarkQuestion> searchQuestions(@Param("search") String search, Pageable pageable);
}
