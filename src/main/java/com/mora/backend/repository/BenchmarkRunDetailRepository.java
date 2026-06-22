package com.mora.backend.repository;

import com.mora.backend.model.entity.BenchmarkRunDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BenchmarkRunDetailRepository extends JpaRepository<BenchmarkRunDetail, Long> {
    List<BenchmarkRunDetail> findByBenchmarkRunId(Long runId);
}
