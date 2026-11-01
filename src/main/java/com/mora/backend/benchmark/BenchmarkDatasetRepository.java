package com.mora.backend.benchmark;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BenchmarkDatasetRepository extends JpaRepository<BenchmarkDataset,Long> { java.util.List<BenchmarkDataset> findAllByOrderByIdDesc(); }
