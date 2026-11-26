package com.rayvolt.backend.service;

import com.rayvolt.backend.errors.BadRequestException;
import com.rayvolt.backend.errors.NotFoundException;
import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterStateChange;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.model.StateChangeCause;
import com.rayvolt.backend.repository.HubRepository;
import com.rayvolt.backend.repository.ScooterRepository;
import com.rayvolt.backend.repository.ScooterStateHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;

@Service
public class ScooterLifecycleService {
    private final ScooterRepository scooters;
    private final HubRepository hubs;
    private final ScooterStateHistoryRepository history;
    private final Clock clock;

    public ScooterLifecycleService(ScooterRepository scooters, HubRepository hubs,
            ScooterStateHistoryRepository history, Clock clock) {
        this.scooters = scooters;
        this.hubs = hubs;
        this.history = history;
        this.clock = clock;
    }

    @Transactional
    public Scooter changeStatus(Long id, ScooterStatus target, StateChangeCause cause) {
        if (cause == null) {
            throw new BadRequestException("State change cause is required");
        }
        Scooter scooter = scooters.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Scooter not found"));
        ScooterStatus before = scooter.getStatus();
        scooter.changeStatus(target);
        hubs.findByIdForUpdate(scooter.getHubId()).orElseThrow(() -> new NotFoundException("Hub not found"));
        Scooter saved = scooters.save(scooter);
        recordChange(before, saved, cause);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ScooterStateChange> history(Long id) {
        if (!scooters.existsById(id)) {
            throw new NotFoundException("Scooter not found");
        }
        return history.findAllByScooterIdOrderByIdAsc(id);
    }

    void recordChange(ScooterStatus from, Scooter scooter, StateChangeCause cause) {
        history.save(new ScooterStateChange(scooter.getId(), from, scooter.getStatus(), clock.instant(), cause));
    }
}
