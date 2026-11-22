package com.rayvolt.backend.fleet.repository;

import com.rayvolt.backend.fleet.model.Scooter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public interface FleetRepository {
    List<Scooter> findAll();
    Optional<Scooter> findById(UUID id);
    Optional<Scooter> findByIdForUpdate(UUID id);
    void lockHubs(UUID... hubIds);
    Scooter save(Scooter scooter);
    boolean hasSpace(UUID hubId);

    // Fleet mutations must be atomic and roll back on failure.
    // Lock the scooter first, then hubs in UUID order before checking capacity or saving.
    // Booking/trip state changes must use the same locking protocol.
    <T> T withTransaction(Supplier<T> operation);
}
