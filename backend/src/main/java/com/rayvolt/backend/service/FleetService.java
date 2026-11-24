package com.rayvolt.backend.service;

import com.rayvolt.backend.errors.ConflictException;
import com.rayvolt.backend.errors.NotFoundException;
import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.repository.HubRepository;
import com.rayvolt.backend.repository.ScooterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Stream;

@Service
public class FleetService {
    private final ScooterRepository scooters;
    private final HubRepository hubs;

    public FleetService(ScooterRepository scooters, HubRepository hubs) {
        this.scooters = scooters;
        this.hubs = hubs;
    }

    public List<Scooter> list() {
        return scooters.findAllByOrderByIdAsc();
    }

    public Scooter get(Long id) {
        return scooters.findById(id).orElseThrow(() -> new NotFoundException("Scooter not found"));
    }

    @Transactional
    public Scooter add(String type, Long hubId) {
        Scooter scooter = new Scooter(type, hubId);
        requireSpace(lockHubs(hubId).getFirst());
        return scooters.save(scooter);
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
        Scooter scooter = find(id);
        scooter.requireEditable();
        lockHubs(scooter.getHubId());
        scooter.retire();
        return scooters.save(scooter);
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
