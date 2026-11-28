package com.rayvolt.backend.service;

import com.rayvolt.backend.errors.ConflictException;
import com.rayvolt.backend.errors.NotFoundException;
import com.rayvolt.backend.model.*;
import com.rayvolt.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Service
public class TripService {
    private final TripRepository trips;
    private final ScooterRepository scooters;
    private final ScooterStateRepository states;
    private final HubRepository hubs;
    private final ScooterLifecycleService lifecycle;
    private final CommandService commands;
    private final SseService sse;
    private final Clock clock;

    public TripService(TripRepository trips, ScooterRepository scooters, ScooterStateRepository states,
            HubRepository hubs, ScooterLifecycleService lifecycle, CommandService commands,
            SseService sse, Clock clock) {
        this.trips = trips;
        this.scooters = scooters;
        this.states = states;
        this.hubs = hubs;
        this.lifecycle = lifecycle;
        this.commands = commands;
        this.sse = sse;
        this.clock = clock;
    }

    @Transactional
    public Trip end(Long id) {
        Long scooterId = trips.findScooterIdById(id).orElseThrow(() -> new NotFoundException("Trip not found"));
        Scooter scooter = scooters.findByIdForUpdate(scooterId)
                .orElseThrow(() -> new NotFoundException("Scooter not found"));
        // Lock the scooter first, matching telemetry updates and other fleet operations.
        Trip trip = trips.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Trip not found"));
        trip.requireActive();
        if (scooter.getStatus() != ScooterStatus.IN_USE) {
            throw new ConflictException("Scooter is not in use");
        }
        ScooterState state = states.findById(scooter.getId())
                .orElseThrow(() -> new ConflictException("Trip cannot end without telemetry"));
        if (state.getReadingAt().isBefore(trip.getStartedAt())) {
            throw new ConflictException("Trip requires telemetry from the current ride");
        }
        Long hubId = state.getPositionStatus() == PositionStatus.AT_HUB ? state.getHubId() : null;
        if (hubId != null && !hubs.existsById(hubId)) {
            throw new ConflictException("Telemetry references an unknown hub");
        }
        if (state.getPositionStatus() == PositionStatus.AT_HUB && hubId == null) {
            throw new ConflictException("Telemetry hub is missing");
        }
        trip.end(clock.instant(), state, hubId);
        lifecycle.changeStatus(scooter.getId(), state.isFault() ? ScooterStatus.MAINTENANCE
                : ScooterStatus.AVAILABLE, StateChangeCause.USER);
        if (hubId != null) {
            scooter.returnToHub(hubId);
            scooters.save(scooter);
        }
        Trip saved = trips.save(trip);
        commands.send(scooter.getId(), ScooterCommand.LOCK);
        AfterCommit.run(() -> sse.publish(scooter, state));
        return saved;
    }
}
