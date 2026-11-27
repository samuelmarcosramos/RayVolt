package com.rayvolt.backend.service;

import com.rayvolt.backend.errors.BadRequestException;
import com.rayvolt.backend.errors.ConflictException;
import com.rayvolt.backend.errors.NotFoundException;
import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.model.AvailableScooter;
import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterStateChange;
import com.rayvolt.backend.model.StateChangeCause;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.repository.HubRepository;
import com.rayvolt.backend.repository.ScooterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class FleetService {
    private static final int MINIMUM_BATTERY_PERCENT = 10;
    private static final Set<String> SCOOTER_TYPES = Set.of("standard", "cargo", "patrol");
    private static final Logger LOG = LoggerFactory.getLogger(FleetService.class);
    private final ScooterRepository scooters;
    private final HubRepository hubs;
    private final ScooterGateway gateway;

    private final ScooterLifecycleService lifecycle;

    public FleetService(ScooterRepository scooters, HubRepository hubs, ScooterGateway gateway,
            ScooterLifecycleService lifecycle) {
        this.scooters = scooters;
        this.hubs = hubs;
        this.gateway = gateway;
        this.lifecycle = lifecycle;
    }

    public List<Scooter> list() {
        return scooters.findAllByOrderByIdAsc();
    }

    public List<AvailableScooter> findAvailable(Long hubId, String type) {
        if (hubId == null || hubId <= 0) {
            throw new BadRequestException("A positive hub ID is required");
        }
        if (type != null && type.isBlank()) {
            throw new BadRequestException("Scooter type must not be blank");
        }
        String normalizedType = type == null ? null : type.strip();
        if (normalizedType != null && !SCOOTER_TYPES.contains(normalizedType.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Unsupported scooter type");
        }
        return scooters.findAvailable(hubId, normalizedType == null ? "" : normalizedType, MINIMUM_BATTERY_PERCENT);
    }

    public Scooter get(Long id) {
        return scooters.findById(id).orElseThrow(() -> new NotFoundException("Scooter not found"));
    }

    @Transactional
    public Scooter add(String type, Long hubId) {
        Scooter scooter = new Scooter(type, hubId);
        requireSpace(lockHubs(hubId).getFirst());
        Scooter saved = scooters.save(scooter);
        lifecycle.recordChange(null, saved, StateChangeCause.OPERATOR);
        afterCommit(() -> gateway.registerScooter(saved.getId(), saved.getHubId()));
        return saved;
    }

    @Transactional
    public Scooter edit(Long id, String type, Long hubId) {
        Scooter scooter = find(id);
        scooter.requireEditable();
        List<Hub> locked = lockHubs(scooter.getHubId(), hubId);
        if (!scooter.getHubId().equals(hubId)) {
            requireSpace(locked.stream().filter(hub -> hub.getId().equals(hubId)).findFirst().orElseThrow());
        }
        scooter.edit(type, hubId);
        return scooters.save(scooter);
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
        Scooter saved = lifecycle.changeStatus(id, target, cause);
        if (target == ScooterStatus.RETIRED) {
            afterCommit(() -> gateway.retireScooter(saved.getId(), saved.getHubId()));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ScooterStateChange> history(Long id) {
        return lifecycle.history(id);
    }

    private void afterCommit(Runnable publish) {
        AfterCommit.run(() -> {
            try {
                publish.run();
            } catch (RuntimeException exception) {
                LOG.warn("Could not send the scooter change, it is sent again on the next connection: {}",
                        exception.getMessage());
            }
        });
    }

    private Scooter find(Long id) {
        return scooters.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Scooter not found"));
    }

    private List<Hub> lockHubs(Long... hubIds) {
        return Stream.of(hubIds).distinct().sorted()
                .map(id -> hubs.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Hub not found")))
                .toList();
    }

    private void requireSpace(Hub hub) {
        if (scooters.countByHubIdAndStatusNot(hub.getId(), ScooterStatus.RETIRED) >= hub.getCapacity()) {
            throw new ConflictException("Destination hub is full");
        }
    }
}
