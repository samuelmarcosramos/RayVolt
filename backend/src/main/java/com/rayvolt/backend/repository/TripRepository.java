package com.rayvolt.backend.repository;

import com.rayvolt.backend.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {
    boolean existsByRiderIdAndEndedAtIsNull(Long riderId);
    boolean existsByScooterIdAndEndedAtIsNull(Long scooterId);

    @Query("SELECT t.scooterId FROM Trip t WHERE t.id = :id")
    Optional<Long> findScooterIdById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trip t WHERE t.id = :id")
    Optional<Trip> findByIdForUpdate(Long id);
}

