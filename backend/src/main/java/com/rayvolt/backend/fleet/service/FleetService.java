package com.rayvolt.backend.fleet.service;

import com.rayvolt.backend.common.exception.ConflictException;
import com.rayvolt.backend.common.exception.BadRequestException;
import com.rayvolt.backend.fleet.model.Scooter;
import com.rayvolt.backend.fleet.model.ScooterStateChange;
import com.rayvolt.backend.fleet.model.StateChangeCause;
import com.rayvolt.backend.fleet.model.ScooterStatus;
import com.rayvolt.backend.fleet.repository.FleetRepository;
import com.rayvolt.backend.fleet.repository.ScooterStateHistoryRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.rayvolt.backend.common.exception.NotFoundException;
import java.util.Objects;

@Service
public class FleetService {
    private final FleetRepository repository;

    private final ScooterStateHistoryRepository history;
    private final Clock clock;

    public FleetService(FleetRepository repository, ScooterStateHistoryRepository history, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.history = Objects.requireNonNull(history);
        this.clock = Objects.requireNonNull(clock);
    }

    public List<Scooter> list() {
        return repository.findAll();
    }

    public Scooter get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Scooter not found"));
    }

    @Transactional
    public Scooter add(String type, Long hubId) {
        Scooter scooter = new Scooter(null, type, hubId, ScooterStatus.AVAILABLE);
        repository.lockHubs(hubId);
        requireSpace(hubId);
        Scooter created = repository.save(scooter);
        recordChange(null, created, StateChangeCause.OPERATOR);
        return created;
    }

    @Transactional
    public Scooter edit(Long id, String type, Long hubId) {
        Scooter current = find(id);
        Scooter updated = current.edit(type, hubId);
        repository.lockHubs(current.hubId(), hubId);
        if (!current.hubId().equals(hubId)) {
            requireSpace(hubId);
        }
        return repository.save(updated);
    }

    @Transactional
    public Scooter retire(Long id) {
        return changeStatus(id, ScooterStatus.RETIRED, StateChangeCause.OPERATOR);
    }

    @Transactional
    public Scooter changeOperatorStatus(Long id, ScooterStatus target) {
        if (target == ScooterStatus.RESERVED || target == ScooterStatus.IN_USE) {
            throw new ConflictException("Reservations and rides must use the booking and trip flows");
        }
        return changeStatus(id, target, StateChangeCause.OPERATOR);
    }

    @Transactional
    public Scooter changeStatus(Long id, ScooterStatus target, StateChangeCause cause) {
        if (cause == null) {
            throw new BadRequestException("State change cause is required");
        }
        Scooter current = find(id);
        Scooter changed = current.changeStatus(target);
        repository.lockHubs(current.hubId());
        Scooter saved = repository.save(changed);
        recordChange(current.status(), saved, cause);
        return saved;
    }

    public List<ScooterStateChange> history(Long id) {
        get(id);
        return history.findByScooterId(id);
    }

    private void recordChange(ScooterStatus from, Scooter scooter, StateChangeCause cause) {
        history.append(new ScooterStateChange(null, scooter.id(), from, scooter.status(), clock.instant(), cause));
    }

    private Scooter find(Long id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Scooter not found"));
    }

    private void requireSpace(Long hubId) {
        if (!repository.hasSpace(hubId)) {
            throw new ConflictException("Destination hub is full");
        }
    }
}
