package com.mora.backend.benchmark;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BenchmarkQuestionRepository extends JpaRepository<BenchmarkQuestion,Long> { java.util.List<BenchmarkQuestion> findByDatasetIdOrderByIdAsc(Long datasetId); void deleteByDatasetId(Long datasetId); }
