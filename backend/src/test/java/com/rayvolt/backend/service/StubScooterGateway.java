package com.rayvolt.backend.service;

import com.rayvolt.backend.model.CommandReply;
import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.model.ScooterCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.function.Consumer;

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

    @Override
    public void sendCommand(Long scooterId, String commandId, ScooterCommand command) {
    }

    @Override
    public void onReply(Consumer<CommandReply> listener) {
    }
}
