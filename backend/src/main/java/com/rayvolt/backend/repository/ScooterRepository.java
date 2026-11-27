package com.rayvolt.backend.repository;

import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.AvailableScooter;
import com.rayvolt.backend.model.ScooterStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ScooterRepository extends JpaRepository<Scooter, Long> {
    List<Scooter> findAllByOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Scooter s WHERE s.id = :id")
    Optional<Scooter> findByIdForUpdate(Long id);

    long countByHubIdAndStatusNot(Long hubId, ScooterStatus status);

    @Query("""
            SELECT new com.rayvolt.backend.model.AvailableScooter(s.id, s.type, s.hubId, s.status, state.battery)
            FROM Scooter s LEFT JOIN ScooterState state ON state.scooterId = s.id
            WHERE s.hubId = :hubId
              AND s.status = com.rayvolt.backend.model.ScooterStatus.AVAILABLE
              AND (state.battery IS NULL OR state.battery >= :minimumBatteryPercent)
              AND (:type = '' OR LOWER(s.type) = LOWER(:type))
            ORDER BY s.id
            """)
    List<AvailableScooter> findAvailable(@Param("hubId") Long hubId, @Param("type") String type,
            @Param("minimumBatteryPercent") int minimumBatteryPercent);
}
