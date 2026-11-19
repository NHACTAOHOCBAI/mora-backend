package com.mora.backend.benchmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface BenchmarkSampleRepository extends JpaRepository<BenchmarkSample, Long> {
    List<BenchmarkSample> findByRunIdOrderByIdAsc(Long runId);

    @Modifying
    @Query("delete from BenchmarkSample sample where sample.runId = :runId")
    void deleteSamplesForRun(@Param("runId") long runId);

    @Modifying
    @Query("delete from BenchmarkSample sample where sample.runId in :runIds")
    void deleteSamplesForRuns(@Param("runIds") List<Long> runIds);
}
