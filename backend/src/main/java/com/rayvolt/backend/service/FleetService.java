package com.rayvolt.backend.service;

import com.rayvolt.backend.errors.ConflictException;
import com.rayvolt.backend.errors.NotFoundException;
import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.repository.HubRepository;
import com.rayvolt.backend.repository.ScooterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.stream.Stream;

@Service
public class FleetService {
    private static final Logger LOG = LoggerFactory.getLogger(FleetService.class);
    private final ScooterRepository scooters;
    private final HubRepository hubs;
    private final ScooterGateway gateway;

    public FleetService(ScooterRepository scooters, HubRepository hubs, ScooterGateway gateway) {
        this.scooters = scooters;
        this.hubs = hubs;
        this.gateway = gateway;
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
        Scooter saved = scooters.save(scooter);
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
        Scooter scooter = find(id);
        scooter.requireEditable();
        lockHubs(scooter.getHubId());
        scooter.retire();
        Scooter saved = scooters.save(scooter);
        afterCommit(() -> gateway.retireScooter(saved.getId(), saved.getHubId()));
        return saved;
    }

    private void afterCommit(Runnable publish) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    publish.run();
                } catch (RuntimeException exception) {
                    LOG.warn("Could not send the scooter change, it is sent again on the next connection: {}",
                            exception.getMessage());
                }
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
