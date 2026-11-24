package com.rayvolt.backend.service;

import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterState;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.model.TelemetryReading;
import com.rayvolt.backend.repository.ScooterRepository;
import com.rayvolt.backend.repository.ScooterStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.Optional;

@Service
public class TelemetryService {
    private static final Logger LOG = LoggerFactory.getLogger(TelemetryService.class);
    private final ScooterRepository scooters;
    private final ScooterStateRepository states;
    private final Clock clock;

    public TelemetryService(ScooterRepository scooters, ScooterStateRepository states, Clock clock) {
        this.scooters = scooters;
        this.states = states;
        this.clock = clock;
    }

    @Transactional
    public void record(TelemetryReading reading) {
        boolean active = scooters.findById(reading.scooterId())
                .filter(scooter -> scooter.getStatus() != ScooterStatus.RETIRED)
                .isPresent();
        if (!active) {
            LOG.warn("Ignored telemetry for unknown or retired scooter {}", reading.scooterId());
            return;
        }
        Optional<ScooterState> stored = states.findById(reading.scooterId());
        if (stored.isPresent() && !stored.get().isNewer(reading)) {
            LOG.debug("Ignored old or repeated telemetry for scooter {}", reading.scooterId());
            return;
        }
        ScooterState state = stored.orElse(null);
        if (state == null) {
            state = new ScooterState(reading, clock.instant());
        } else {
            state.update(reading, clock.instant());
        }
        states.save(state);
        if (reading.fault()) {
            recordFault(reading.scooterId());
        }
    }

    @Transactional
    public void recordFault(Long scooterId) {
        Optional<Scooter> found = scooters.findByIdForUpdate(scooterId)
                .filter(scooter -> scooter.getStatus() != ScooterStatus.RETIRED);
        if (found.isEmpty()) {
            LOG.warn("Ignored fault for unknown or retired scooter {}", scooterId);
            return;
        }
        Scooter scooter = found.get();
        ScooterStatus before = scooter.getStatus();
        scooter.markFaulty();
        if (scooter.getStatus() != before) {
            scooters.save(scooter);
            LOG.info("Scooter {} reported a fault and moved from {} to {}", scooterId, before, scooter.getStatus());
        }
    }
}
