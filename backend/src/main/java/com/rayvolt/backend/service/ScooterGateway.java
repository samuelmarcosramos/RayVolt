package com.rayvolt.backend.service;

import com.rayvolt.backend.model.CommandReply;
import com.rayvolt.backend.model.Hub;
import com.rayvolt.backend.model.ScooterCommand;
import java.util.List;
import java.util.function.Consumer;

public interface ScooterGateway {
    void publishHubs(List<Hub> hubs);

    void registerScooter(Long scooterId, Long hubId);

    void retireScooter(Long scooterId, Long hubId);

    void sendCommand(Long scooterId, String commandId, ScooterCommand command);

    void onConnected(Runnable action);

    void onReply(Consumer<CommandReply> listener);
}
