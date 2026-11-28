package com.rayvolt.backend.repository;

import com.rayvolt.backend.model.TripBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;

public interface TripBookingRepository extends JpaRepository<TripBooking, Long> {
    @Query("SELECT b.scooterId FROM TripBooking b WHERE b.expiresAt <= :now ORDER BY b.scooterId")
    List<Long> findExpiredScooterIds(Instant now);

    List<TripBooking> findByRiderIdAndExpiresAtAfter(Long riderId, Instant now);
}
