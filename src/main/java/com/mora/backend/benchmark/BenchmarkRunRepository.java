package com.mora.backend.benchmark;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun,Long> { java.util.List<BenchmarkRun> findAllByOrderByIdDesc(); java.util.List<BenchmarkRun> findByStatus(String status); }
