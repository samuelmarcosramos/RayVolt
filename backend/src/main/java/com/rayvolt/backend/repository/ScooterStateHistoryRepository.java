package com.rayvolt.backend.repository;

import com.rayvolt.backend.model.ScooterStateChange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ScooterStateHistoryRepository extends JpaRepository<ScooterStateChange, Long> {
    List<ScooterStateChange> findAllByScooterIdOrderByIdAsc(Long scooterId);
}
