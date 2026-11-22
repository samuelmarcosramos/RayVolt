package com.rayvolt.backend.fleet.service;

import com.rayvolt.backend.fleet.model.Scooter;
import com.rayvolt.backend.fleet.model.ScooterStatus;
import com.rayvolt.backend.fleet.repository.FleetRepository;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@org.springframework.stereotype.Service
public class FleetService {
    private final FleetRepository repository;

    public FleetService(FleetRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public Scooter add(String type, UUID hubId) {
        Scooter scooter = new Scooter(UUID.randomUUID(), type, hubId, ScooterStatus.AVAILABLE);
        return repository.withExclusiveAccess(() -> {
            requireSpace(hubId);
            return repository.save(scooter);
        });
    }

    public Scooter edit(UUID id, String type, UUID hubId) {
        return repository.withExclusiveAccess(() -> {
            Scooter current = find(id);
            Scooter updated = current.edit(type, hubId);
            if (!current.hubId().equals(hubId)) {
                requireSpace(hubId);
            }
            return repository.save(updated);
        });
    }

    public Scooter retire(UUID id) {
        return repository.withExclusiveAccess(() -> repository.save(find(id).retire()));
    }

    private Scooter find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NoSuchElementException("Scooter not found"));
    }

    private void requireSpace(UUID hubId) {
        if (!repository.hasSpace(hubId)) {
            throw new IllegalStateException("Destination hub is full");
        }
    }
}
