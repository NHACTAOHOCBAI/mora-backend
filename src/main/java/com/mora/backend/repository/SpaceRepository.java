package com.mora.backend.repository;

import com.mora.backend.model.entity.Space;
import com.mora.backend.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpaceRepository extends JpaRepository<Space, Long> {
    List<Space> findByUser(User user);

    @Query("SELECT DISTINCT s FROM Space s LEFT JOIN SpaceMember sm ON sm.space = s WHERE s.user = :user OR sm.user = :user ORDER BY s.updatedAt DESC")
    List<Space> findAllAccessibleSpaces(@Param("user") User user);

    boolean existsByIdAndUserId(Long id, Long userId);
}
