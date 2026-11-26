package com.rayvolt.backend.service;

import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterLive;
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
    private final SseService sse;

    public TelemetryService(ScooterRepository scooters, ScooterStateRepository states, Clock clock,
            SseService sse) {
        this.scooters = scooters;
        this.states = states;
        this.clock = clock;
        this.sse = sse;
    }

    @Transactional
    public void record(TelemetryReading reading) {
        Optional<Scooter> active = scooters.findByIdForUpdate(reading.scooterId())
                .filter(scooter -> scooter.getStatus() != ScooterStatus.RETIRED);
        if (active.isEmpty()) {
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
        Scooter scooter = active.get();
        if (reading.fault()) {
            markFaulty(scooter);
        }
        ScooterLive update = ScooterLive.of(scooter, state);
        AfterCommit.run(() -> sse.publish(update));
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
        boolean statusChanged = markFaulty(scooter);
        ScooterState state = states.findById(scooterId).orElse(null);
        boolean flagChanged = state != null && state.markFault();
        if (flagChanged) {
            states.save(state);
        }
        if (statusChanged || flagChanged) {
            ScooterLive update = ScooterLive.of(scooter, state);
            AfterCommit.run(() -> sse.publish(update));
        }
    }

    private boolean markFaulty(Scooter scooter) {
        ScooterStatus before = scooter.getStatus();
        scooter.markFaulty();
        if (scooter.getStatus() == before) {
            return false;
        }
        scooters.save(scooter);
        LOG.info("Scooter {} reported a fault and moved from {} to {}", scooter.getId(), before, scooter.getStatus());
        return true;
    }
}
