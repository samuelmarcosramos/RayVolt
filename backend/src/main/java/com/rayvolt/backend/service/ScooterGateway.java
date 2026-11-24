package com.rayvolt.backend.service;

import com.rayvolt.backend.model.Hub;
import java.util.List;

public interface ScooterGateway {
    void publishHubs(List<Hub> hubs);

    void registerScooter(Long scooterId, Long hubId);

    void retireScooter(Long scooterId, Long hubId);

    void onConnected(Runnable action);
}
