package com.rayvolt.backend.fleet.service;

import com.rayvolt.backend.common.exception.ConflictException;
import com.rayvolt.backend.fleet.model.Scooter;
import com.rayvolt.backend.fleet.model.ScooterStatus;
import com.rayvolt.backend.fleet.repository.FleetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.rayvolt.backend.common.exception.NotFoundException;
import java.util.Objects;
import java.util.UUID;

@Service
public class FleetService {
    private final FleetRepository repository;

    public FleetService(FleetRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public List<Scooter> list() {
        return repository.findAll();
    }

    @Transactional
    public Scooter add(String type, UUID hubId) {
        Scooter scooter = new Scooter(UUID.randomUUID(), type, hubId, ScooterStatus.AVAILABLE);
        repository.lockHubs(hubId);
        requireSpace(hubId);
        return repository.save(scooter);
    }

    @Transactional
    public Scooter edit(UUID id, String type, UUID hubId) {
        Scooter current = find(id);
        Scooter updated = current.edit(type, hubId);
        repository.lockHubs(current.hubId(), hubId);
        if (!current.hubId().equals(hubId)) {
            requireSpace(hubId);
        }
        return repository.save(updated);
    }

    @Transactional
    public Scooter retire(UUID id) {
        Scooter current = find(id);
        Scooter retired = current.retire();
        repository.lockHubs(current.hubId());
        return repository.save(retired);
    }

    private Scooter find(UUID id) {
        return repository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Scooter not found"));
    }

    private void requireSpace(UUID hubId) {
        if (!repository.hasSpace(hubId)) {
            throw new ConflictException("Destination hub is full");
        }
    }
}
