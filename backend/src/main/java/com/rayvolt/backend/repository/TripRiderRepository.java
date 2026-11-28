package com.rayvolt.backend.repository;

import com.rayvolt.backend.model.TripRider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface TripRiderRepository extends JpaRepository<TripRider, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM TripRider r WHERE r.id = :id")
    Optional<TripRider> findByIdForUpdate(Long id);
}
