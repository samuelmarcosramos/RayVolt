package com.rayvolt.backend.service;

import com.rayvolt.backend.errors.*;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class BookingService {
    private final TripBookingRepository bookings;
    private final TripRepository trips;
    private final TripRiderRepository riders;
    private final TripPolicyRepository policies;
    private final ScooterRepository scooters;
    private final ScooterStateRepository states;
    private final ScooterLifecycleService lifecycle;
    private final SseService sse;
    private final Clock clock;

    public BookingService(TripBookingRepository bookings, TripRepository trips, TripRiderRepository riders,
            TripPolicyRepository policies, ScooterRepository scooters, ScooterStateRepository states,
            ScooterLifecycleService lifecycle, SseService sse, Clock clock) {
        this.bookings = bookings;
        this.trips = trips;
        this.riders = riders;
        this.policies = policies;
        this.scooters = scooters;
        this.states = states;
        this.lifecycle = lifecycle;
        this.sse = sse;
        this.clock = clock;
    }

    @Transactional
    public TripBooking book(Long riderId, Long scooterId) {
        TripRider rider = lockRider(riderId);
        TripPolicy policy = policies.findById(rider.getCategory())
                .orElseThrow(() -> new ForbiddenException("No trip policy configured for this rider category"));
        Instant now = clock.instant();
        rider.requireActive(now.atZone(policy.zone()).toLocalDate());
        if (trips.existsByRiderIdAndEndedAtIsNull(riderId)
                || !bookings.findByRiderIdAndExpiresAtAfter(riderId, now).isEmpty()) {
            throw new ConflictException("Rider already has an active booking or trip");
        }
        Scooter scooter = lockScooter(scooterId);
        expireLocked(scooter, now);
        if (scooter.getStatus() != ScooterStatus.AVAILABLE || bookings.existsById(scooterId)
                || trips.existsByScooterIdAndEndedAtIsNull(scooterId)) {
            throw new ConflictException("Only an available scooter can be booked");
        }
        ScooterState state = states.findById(scooterId)
                .orElseThrow(() -> new ConflictException("Scooter telemetry is required to book"));
        state.requireReadyAtHub(scooter.getHubId());
        policy.requireAllowed(now, scooter.getType(), state.getBattery());
        TripBooking booking = bookings.save(TripBooking.reserve(scooterId, riderId, now));
        lifecycle.changeStatus(scooterId, ScooterStatus.RESERVED, StateChangeCause.USER);
        AfterCommit.run(() -> sse.publish(scooter, state));
        return booking;
    }

    @Transactional
    public void cancel(Long riderId, Long scooterId) {
        lockRider(riderId);
        Scooter scooter = lockScooter(scooterId);
        TripBooking booking = bookings.findById(scooterId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (!booking.getRiderId().equals(riderId)) {
            throw new ForbiddenException("Only the booking owner can cancel it");
        }
        booking.requireOwnedBy(riderId, clock.instant());
        bookings.delete(booking);
        release(scooter, StateChangeCause.USER);
        publish(scooter);
    }

    @Transactional(readOnly = true)
    public List<Long> expiredScooters() {
        return bookings.findExpiredScooterIds(clock.instant());
    }

    @Transactional
    public void expire(Long scooterId) {
        scooters.findByIdForUpdate(scooterId).ifPresent(scooter -> {
            if (expireLocked(scooter, clock.instant())) {
                publish(scooter);
            }
        });
    }

    private boolean expireLocked(Scooter scooter, Instant now) {
        TripBooking booking = bookings.findById(scooter.getId()).orElse(null);
        if (booking == null || !booking.isExpiredAt(now)) {
            return false;
        }
        bookings.delete(booking);
        bookings.flush();
        release(scooter, StateChangeCause.SYSTEM);
        return true;
    }

    private void release(Scooter scooter, StateChangeCause cause) {
        // A fault or operator action may have moved the scooter to maintenance already.
        if (scooter.getStatus() == ScooterStatus.RESERVED) {
            lifecycle.changeStatus(scooter.getId(), ScooterStatus.AVAILABLE, cause);
        }
    }

    private void publish(Scooter scooter) {
        ScooterState state = states.findById(scooter.getId()).orElse(null);
        AfterCommit.run(() -> sse.publish(scooter, state));
    }

    private TripRider lockRider(Long id) {
        return riders.findByIdForUpdate(id).orElseThrow(() -> new ForbiddenException("Rider account not found"));
    }

    private Scooter lockScooter(Long id) {
        return scooters.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Scooter not found"));
    }
}
