package com.rayvolt.backend.fleet.service;

import com.rayvolt.backend.common.exception.ConflictException;
import com.rayvolt.backend.common.exception.BadRequestException;
import com.rayvolt.backend.fleet.model.Scooter;
import com.rayvolt.backend.fleet.model.ScooterStatus;
import com.rayvolt.backend.fleet.model.Hub;
import com.rayvolt.backend.fleet.repository.FleetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.rayvolt.backend.common.exception.NotFoundException;
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

    public List<Scooter> findAvailable(Long hubId, String type, int minimumBatteryPercent) {
        if (hubId == null || hubId <= 0) {
            throw new BadRequestException("A positive hub ID is required");
        }
        if (minimumBatteryPercent < 0 || minimumBatteryPercent > 100) {
            throw new BadRequestException("Minimum battery percentage must be between 0 and 100");
        }
        if (type != null && type.isBlank()) {
            throw new BadRequestException("Scooter type must not be blank");
        }
        return repository.findAvailable(hubId, type == null ? null : type.strip(), minimumBatteryPercent);
    }

    public List<Hub> listHubs() {
        return repository.findHubs();
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
