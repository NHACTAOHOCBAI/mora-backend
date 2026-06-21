package com.mora.backend.repository;

import com.mora.backend.model.entity.Space;
import com.mora.backend.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpaceRepository extends JpaRepository<Space, Long> {
    List<Space> findByUser(User user);
}
