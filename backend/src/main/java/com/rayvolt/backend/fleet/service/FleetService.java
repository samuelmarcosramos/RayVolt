package com.rayvolt.backend.fleet.service;

import com.rayvolt.backend.fleet.model.Scooter;
import com.rayvolt.backend.fleet.model.ScooterStatus;
import com.rayvolt.backend.fleet.repository.FleetRepository;
import org.springframework.stereotype.Service;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Service
public class FleetService {
    private final FleetRepository repository;

    public FleetService(FleetRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public Scooter add(String type, UUID hubId) {
        Scooter scooter = new Scooter(UUID.randomUUID(), type, hubId, ScooterStatus.AVAILABLE);
        return repository.withTransaction(() -> {
            repository.lockHubs(hubId);
            requireSpace(hubId);
            return repository.save(scooter);
        });
    }

    public Scooter edit(UUID id, String type, UUID hubId) {
        return repository.withTransaction(() -> {
            Scooter current = find(id);
            Scooter updated = current.edit(type, hubId);
            repository.lockHubs(current.hubId(), hubId);
            if (!current.hubId().equals(hubId)) {
                requireSpace(hubId);
            }
            return repository.save(updated);
        });
    }

    public Scooter retire(UUID id) {
        return repository.withTransaction(() -> {
            Scooter current = find(id);
            Scooter retired = current.retire();
            repository.lockHubs(current.hubId());
            return repository.save(retired);
        });
    }

    private Scooter find(UUID id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new NoSuchElementException("Scooter not found"));
    }

    private void requireSpace(UUID hubId) {
        if (!repository.hasSpace(hubId)) {
            throw new IllegalStateException("Destination hub is full");
        }
    }
}
