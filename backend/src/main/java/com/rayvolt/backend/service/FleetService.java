package com.rayvolt.backend.service;

import com.rayvolt.backend.errors.ConflictException;
import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.repository.FleetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.rayvolt.backend.errors.NotFoundException;
import java.util.Objects;

@Service
public class FleetService {
    private final FleetRepository repository;

    public FleetService(FleetRepository repository) {
        this.repository = Objects.requireNonNull(repository);
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
        return repository.save(scooter);
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
        Scooter current = find(id);
        Scooter retired = current.retire();
        repository.lockHubs(current.hubId());
        return repository.save(retired);
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
