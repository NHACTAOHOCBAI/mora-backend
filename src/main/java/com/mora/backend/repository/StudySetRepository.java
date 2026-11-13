package com.mora.backend.repository;
import com.mora.backend.model.entity.StudySet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface StudySetRepository extends JpaRepository<StudySet, Long> { List<StudySet> findBySpaceIdOrderByCreatedAtDesc(Long spaceId); }
