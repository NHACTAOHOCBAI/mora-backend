package com.mora.backend.repository;

import com.mora.backend.model.entity.Space;
import com.mora.backend.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface SpaceRepository extends JpaRepository<Space, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Space s WHERE s.id = :id")
    java.util.Optional<Space> findForUpdate(@Param("id") Long id);

    List<Space> findByUser(User user);

    @Query("SELECT DISTINCT s FROM Space s LEFT JOIN SpaceMember sm ON sm.space = s WHERE s.user = :user OR sm.user = :user ORDER BY s.updatedAt DESC")
    List<Space> findAllAccessibleSpaces(@Param("user") User user);

    boolean existsByIdAndUserId(Long id, Long userId);

    @Query("SELECT s FROM Space s WHERE :search IS NULL OR :search = '' OR " +
            "LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(COALESCE(s.description, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(s.user.fullName) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Space> searchForAdmin(@Param("search") String search, Pageable pageable);
}
