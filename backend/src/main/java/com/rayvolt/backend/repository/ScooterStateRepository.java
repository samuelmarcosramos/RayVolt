package com.rayvolt.backend.repository;

import com.rayvolt.backend.model.ScooterState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScooterStateRepository extends JpaRepository<ScooterState, Long> {
}
