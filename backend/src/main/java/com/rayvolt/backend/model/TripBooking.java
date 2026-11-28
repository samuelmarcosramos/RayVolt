package com.rayvolt.backend.model;

import com.rayvolt.backend.errors.ConflictException;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "trip_bookings")
public class TripBooking {
    @Id private Long scooterId;
    private Long riderId;
    private Instant expiresAt;

    protected TripBooking() { }

    public TripBooking(Long scooterId, Long riderId, Instant expiresAt) {
        if (scooterId == null || scooterId <= 0 || riderId == null || riderId <= 0 || expiresAt == null) {
            throw new IllegalArgumentException("Booking scooter, rider and expiry are required");
        }
        this.scooterId = scooterId;
        this.riderId = riderId;
        this.expiresAt = expiresAt;
    }

    public void requireOwnedBy(Long riderId, Instant now) {
        if (!this.riderId.equals(riderId) || !now.isBefore(expiresAt)) {
            throw new ConflictException("Booking belongs to another rider or has expired");
        }
    }

    public Long getScooterId() { return scooterId; }
}
