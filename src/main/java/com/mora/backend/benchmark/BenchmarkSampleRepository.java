package com.mora.backend.benchmark;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BenchmarkSampleRepository extends JpaRepository<BenchmarkSample,Long> { java.util.List<BenchmarkSample> findByRunIdOrderByIdAsc(Long runId); }
