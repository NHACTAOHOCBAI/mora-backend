package com.mora.backend.benchmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface BenchmarkRunRepository extends JpaRepository<BenchmarkRun, Long> {
    List<BenchmarkRun> findAllByOrderByIdDesc();
    List<BenchmarkRun> findByStatus(String status);

    @Modifying
    @Query("update BenchmarkRun run set run.name = :name where run.id = :id")
    int updateName(@Param("id") long id, @Param("name") String name);

    @Transactional
    @Modifying
    @Query("update BenchmarkRun run set run.status = :status where run.id = :id")
    int updateStatus(@Param("id") long id, @Param("status") String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from BenchmarkRun run where run.id = :id")
    Optional<BenchmarkRun> findForDeletion(@Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from BenchmarkRun run where run.id in :ids order by run.id")
    List<BenchmarkRun> findAllForDeletion(@Param("ids") List<Long> ids);
}
