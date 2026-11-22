package com.rayvolt.backend.fleet.repository;

import com.rayvolt.backend.fleet.model.Scooter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Locking and write methods require a service transaction.
// Lock the scooter first, then hubs in UUID order before checking capacity or saving.
// Booking/trip writers must follow the same protocol.
public interface FleetRepository {
    List<Scooter> findAll();
    Optional<Scooter> findById(UUID id);
    Optional<Scooter> findByIdForUpdate(UUID id);
    void lockHubs(UUID... hubIds);
    Scooter save(Scooter scooter);
    boolean hasSpace(UUID hubId);
}
