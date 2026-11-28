package com.rayvolt.backend.repository;

import com.rayvolt.backend.model.TripBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface TripBookingRepository extends JpaRepository<TripBooking, Long> {
    List<TripBooking> findByRiderIdAndExpiresAtAfter(Long riderId, Instant now);
}
