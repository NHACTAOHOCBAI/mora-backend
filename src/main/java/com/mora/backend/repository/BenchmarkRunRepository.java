package com.mora.backend.repository;

import com.mora.backend.model.entity.BenchmarkRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, Long> {
    List<BenchmarkRun> findAllByOrderByRunAtDesc();

    @Query("SELECT r FROM BenchmarkRun r WHERE " +
           "(:search IS NULL OR :search = '' OR " +
           "LOWER(r.approachName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<BenchmarkRun> searchRuns(@Param("search") String search, Pageable pageable);
}
