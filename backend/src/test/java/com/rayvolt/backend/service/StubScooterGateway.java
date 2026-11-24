package com.rayvolt.backend.service;

import com.rayvolt.backend.model.Hub;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@ConditionalOnProperty(name = "rayvolt.mqtt.enabled", havingValue = "false")
public class StubScooterGateway implements ScooterGateway {
    @Override
    public void publishHubs(List<Hub> hubs) {
    }

    @Override
    public void registerScooter(Long scooterId, Long hubId) {
    }

    @Override
    public void retireScooter(Long scooterId, Long hubId) {
    }

    @Override
    public void onConnected(Runnable action) {
    }
}
