package com.rayvolt.backend.service;

import com.rayvolt.backend.billing.DepositHold;
import com.rayvolt.backend.billing.DepositService;
import com.rayvolt.backend.errors.*;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;

@Service
public class TripStartService {
    private final TripRepository trips;
    private final TripRiderRepository riders;
    private final TripPolicyRepository policies;
    private final TripBookingRepository bookings;
    private final ScooterRepository scooters;
    private final ScooterStateRepository states;
    private final ScooterLifecycleService lifecycle;
    private final CommandService commands;
    private final DepositService deposits;
    private final SseService sse;
    private final Clock clock;

    public TripStartService(TripRepository trips, TripRiderRepository riders, TripPolicyRepository policies,
            TripBookingRepository bookings, ScooterRepository scooters, ScooterStateRepository states,
            ScooterLifecycleService lifecycle, CommandService commands, DepositService deposits,
            SseService sse, Clock clock) {
        this.trips = trips;
        this.riders = riders;
        this.policies = policies;
        this.bookings = bookings;
        this.scooters = scooters;
        this.states = states;
        this.lifecycle = lifecycle;
        this.commands = commands;
        this.deposits = deposits;
        this.sse = sse;
        this.clock = clock;
    }

    @Transactional
    public Trip start(Long riderId, Long scooterId) {
        TripRider rider = riders.findByIdForUpdate(riderId)
                .orElseThrow(() -> new ForbiddenException("Rider account not found"));
        TripPolicy policy = policies.findById(rider.getCategory())
                .orElseThrow(() -> new ForbiddenException("No trip policy configured for this rider category"));
        Instant now = clock.instant();
        rider.requireActive(now.atZone(policy.zone()).toLocalDate());
        if (trips.existsByRiderIdAndEndedAtIsNull(riderId)) {
            throw new ConflictException("Rider already has an active trip");
        }
        Scooter scooter = scooters.findByIdForUpdate(scooterId)
                .orElseThrow(() -> new NotFoundException("Scooter not found"));
        requireAvailable(scooter, riderId, now);
        ScooterState state = states.findById(scooterId)
                .orElseThrow(() -> new ConflictException("Current scooter telemetry is required"));
        state.requireReadyAtHub(scooter.getHubId());
        policy.requireAllowed(now, scooter.getType(), state.getBattery());
        if (trips.existsByScooterIdAndEndedAtIsNull(scooterId)) {
            throw new ConflictException("Scooter already has an active trip");
        }
        DepositHold hold = rider.getCategory().requiresDeposit() ? deposits.hold(riderId) : null;
        Trip trip = trips.save(new Trip(riderId, scooterId, now, state.getHubId(), state.getOdometerKm(),
                policy.getMaxMinutes(), policy.getOvertimeRate()));
        if (hold != null) {
            deposits.attach(hold, trip.getId());
        }
        lifecycle.changeStatus(scooterId, ScooterStatus.IN_USE, StateChangeCause.USER);
        bookings.findById(scooterId).ifPresent(bookings::delete);
        commands.send(scooterId, ScooterCommand.UNLOCK);
        AfterCommit.run(() -> sse.publish(scooter, state));
        return trip;
    }

    private void requireAvailable(Scooter scooter, Long riderId, Instant now) {
        if (scooter.getStatus() != ScooterStatus.AVAILABLE && scooter.getStatus() != ScooterStatus.RESERVED) {
            throw new ConflictException("Scooter must be available or reserved by this rider");
        }
        if (scooter.getStatus() == ScooterStatus.RESERVED) {
            bookings.findById(scooter.getId())
                    .orElseThrow(() -> new ConflictException("Scooter reservation not found"))
                    .requireOwnedBy(riderId, now);
        }
        for (TripBooking booking : bookings.findByRiderIdAndExpiresAtAfter(riderId, now)) {
            if (!booking.getScooterId().equals(scooter.getId())) {
                throw new ConflictException("Rider has a booking for another scooter");
            }
        }
    }

}
