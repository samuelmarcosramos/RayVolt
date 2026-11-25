package com.rayvolt.backend.fleet.repository;

import com.rayvolt.backend.fleet.model.ScooterStateChange;
import java.util.List;

public interface ScooterStateHistoryRepository {
    void append(ScooterStateChange change);
    List<ScooterStateChange> findByScooterId(Long scooterId);
}
