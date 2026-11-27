package com.rayvolt.backend.service;

import com.rayvolt.backend.errors.ConflictException;
import com.rayvolt.backend.errors.NotFoundException;
import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.model.HubDetails;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.repository.HubRepository;
import com.rayvolt.backend.repository.ScooterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class HubService {
    private static final Logger LOG = LoggerFactory.getLogger(HubService.class);
    private final ScooterGateway gateway;
    private final HubRepository hubs;
    private final ScooterRepository scooters;

    public HubService(HubRepository hubs, ScooterRepository scooters, ScooterGateway gateway) {
        this.gateway = gateway;
        this.hubs = hubs;
        this.scooters = scooters;
    }

    @Transactional(readOnly = true)
    public List<Hub> list() { return hubs.findAllByOrderByNameAsc(); }

    @Transactional(readOnly = true)
    public Hub get(Long id) {
        return hubs.findById(id).orElseThrow(() -> new NotFoundException("Hub not found"));
    }

    @Transactional
    public Hub add(HubDetails details) {
        if (hubs.existsByName(details.name())) throw new ConflictException("Hub name must be unique");
        return save(new Hub(details));
    }

    @Transactional
    public Hub edit(Long id, HubDetails details) {
        Hub hub = hubs.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Hub not found"));
        if (hubs.existsByNameAndIdNot(details.name(), id)) throw new ConflictException("Hub name must be unique");
        // Fleet operations acquire this same hub lock before changing occupancy.
        long occupancy = scooters.countByHubIdAndStatusNot(id, ScooterStatus.RETIRED);
        if (details.capacity() < occupancy) {
            throw new ConflictException("Hub capacity cannot be below its current scooter count (" + occupancy + ")");
        }
        hub.edit(details);
        return save(hub);
    }

    private void publishHubs() {
        try {
            gateway.publishHubs(hubs.findAllByOrderByNameAsc());
        } catch (RuntimeException exception) {
            LOG.warn("Could not publish hubs; they are sent again on the next connection: {}", exception.getMessage());
        }
    }

    private Hub save(Hub hub) {
        try {
            Hub saved = hubs.saveAndFlush(hub);
            AfterCommit.run(this::publishHubs);
            return saved;
        } catch (DataIntegrityViolationException exception) {
            // The database also prevents concurrent requests from using the same name.
            throw new ConflictException("Hub name must be unique", exception);
        }
    }
}
