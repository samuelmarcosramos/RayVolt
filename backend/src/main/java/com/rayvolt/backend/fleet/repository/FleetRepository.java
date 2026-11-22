package com.rayvolt.backend.fleet.repository;

import com.rayvolt.backend.fleet.model.Scooter;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public interface FleetRepository {
    Optional<Scooter> findById(UUID id);
    Scooter save(Scooter scooter);
    boolean hasSpace(UUID hubId);

    // Fleet mutations must be atomic and roll back on failure.
    // All booking/trip state changes must participate in the same locking protocol.
    <T> T withExclusiveAccess(Supplier<T> operation);
}
