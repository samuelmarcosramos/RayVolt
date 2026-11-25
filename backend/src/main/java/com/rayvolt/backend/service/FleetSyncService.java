package com.rayvolt.backend.service;

import com.rayvolt.backend.model.Scooter;
import com.rayvolt.backend.model.ScooterStatus;
import com.rayvolt.backend.repository.HubRepository;
import com.rayvolt.backend.repository.ScooterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class FleetSyncService {
    private static final Logger LOG = LoggerFactory.getLogger(FleetSyncService.class);
    private final ScooterRepository scooters;
    private final HubRepository hubs;
    private final ScooterGateway gateway;

    public FleetSyncService(ScooterRepository scooters, HubRepository hubs, ScooterGateway gateway) {
        this.scooters = scooters;
        this.hubs = hubs;
        this.gateway = gateway;
        gateway.onConnected(this::publishAll);
    }

    public void publishAll() {
        try {
            gateway.publishHubs(hubs.findAll(Sort.by("id")));
            for (Scooter scooter : scooters.findAllByOrderByIdAsc()) {
                if (scooter.getStatus() == ScooterStatus.RETIRED) {
                    gateway.retireScooter(scooter.getId(), scooter.getHubId());
                } else {
                    gateway.registerScooter(scooter.getId(), scooter.getHubId());
                }
            }
            LOG.info("Published hubs and fleet to the scooters");
        } catch (RuntimeException exception) {
            LOG.warn("Could not publish the fleet, it is sent again on the next connection: {}",
                    exception.getMessage());
        }
    }
}
